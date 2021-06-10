package io.banki;

import static spark.Spark.*;
import com.elarian.model.*;
import com.elarian.*;
import java.util.*;

import com.google.gson.Gson;
import reactor.core.publisher.*;

public class App {

    private static final Elarian elarian;
    private static final int portNumber ;
    private static final String ussdCode;
    private static final MessagingChannel smsChannel;
    private static final ActivityChannel activityChannel;

    static {
        portNumber = 3000;
        ussdCode = System.getenv("USSD_CODE");
        smsChannel = new MessagingChannel(System.getenv("SMS_SHORT_CODE"), MessagingChannel.Channel.SMS);
        activityChannel = new ActivityChannel(System.getenv("APP_ID"), ActivityChannel.Channel.WEB);
        elarian = new Elarian(System.getenv("API_KEY"), System.getenv("ORG_ID"), System.getenv("APP_ID"));
    }

    private static void log(String message) {
        System.out.println(message);
    }

    private static final BaseNotificationHandler<UssdSessionNotification, UssdMenu> ussdHandler = (notification, customer, appData, responder) -> {
        customer.getMetadata()
                .flatMap(metadata -> {
                    DataValue nameVal = metadata.get("name");
                    String step = appData == null ? "home" : appData.string;
                    String name = nameVal != null ? nameVal.string : null;
                    String state = name == null ? "unregisteredUser" : "registeredUser";
                    String input = notification.input;

                    Map<String, DataValue> metaUpdate = new HashMap<>();
                    UssdMenu menu = new UssdMenu("Welcome to Banki", false);
                    ArrayList<Activity> activities = new ArrayList<>();
                    String message = null;
                    switch (state) {
                        case "unregisteredUser":
                            if (step.contentEquals("home") && input.contentEquals("1")) {
                                step = "register";
                            } else if (step.contentEquals("home") && input.contentEquals("2")) {
                                step = "quit";
                            }
                            switch (step) {
                                case "home":
                                    menu.text = "Welcome to Banki\n1 - Register\n2 - Quit";
                                    break;
                                case "register":
                                    menu.text = "Awesome, what is your name?";
                                    step = "register-name";
                                    break;
                                case "register-name":
                                    metaUpdate.put("name", DataValue.of(input));
                                    menu.text = String.format("Welcome %s! We are processing your application and will get back to you shortly.", input);
                                    menu.isTerminal = true;
                                    step = "home";
                                    activities.add(new Activity("UserRegistered", new HashMap<>(), notification.sessionId));
                                    message = String.format("Hey %s, welcome to Banki. Your account is ready!", input);
                                    break;
                                case "quit":
                                    menu.text = "Bye bye, see you next time.";
                                    menu.isTerminal = true;
                                    step = "home";
                                    break;
                            }
                            break;
                        case "registeredUser":

                            if (step.contentEquals("home") && input.contentEquals("1")) {
                                step = "balance";
                            } else if (step.contentEquals("home") && input.contentEquals("2")) {
                                step = "withdraw";
                            }else if (step.contentEquals("home") && input.contentEquals("3")) {
                                step = "quit";
                            }

                            switch (step) {
                                case "home":
                                    menu.text = String.format("Hey %s, welcome to Banki\n1 - Check Balance\n2 - Withdraw\n3 - Quit", name);
                                    break;
                                case "balance":
                                    menu.text = "Your balance is KES 123";
                                    menu.isTerminal = true;
                                    step = "home";

                                    Map<String, String> props = new HashMap<>();
                                    props.put("balance", "123");
                                    activities.add(new Activity("UserCheckedBalance", props, notification.sessionId));

                                    message = String.format("Hi %s, Thank you for using Banki. To serve you better, we would like to run a short survey.\nRespond with Yes to continue.", name);
                                    metaUpdate.put("surveystep", DataValue.of("initial"));
                                    break;
                                case "withdraw":
                                    menu.text = "How much do you need? You can withdraw up to KES 123";
                                    step = "withdraw-amount";
                                    break;
                                case "withdraw-amount":
                                    int amount;
                                    try {
                                        amount = Integer.parseInt(input);
                                        if (amount > 123) {
                                            throw new NumberFormatException("Invalid amount");
                                        }
                                    } catch (NumberFormatException ex) {
                                        menu.text = "Invalid amount, try again.\nHow much do you need? You can withdraw up to KES 123";
                                        break;
                                    }

                                    menu.text = "We are processing your withdrawal request and will get back to you shortly.";
                                    menu.isTerminal = true;
                                    step = "home";
                                    Map<String, String> withdrawalProps = new HashMap<>();
                                    withdrawalProps.put("amount", String.valueOf(amount));
                                    activities.add(new Activity("UserWithdrew", withdrawalProps, notification.sessionId));

                                    // coreBanking.withdraw(amount)

                                    message = String.format("Good news %s, your withdrawal of KES %d has been successfully processed", name, amount);
                                    break;
                                case "quit":
                                    menu.text = String.format("Bye %s, See you next time.", name);
                                    menu.isTerminal = true;
                                    step = "home";
                                    break;
                            }
                            break;
                    }

                    responder.callback(menu, DataValue.of(step));

                    Mono<MessageReply> sendMessage = message == null ? Mono.empty() : customer.sendMessage(smsChannel, new Message(new MessageBody(message)));
                    Flux<CustomerStateUpdateReply> updateActivity = Flux.fromIterable(activities).flatMap(it -> customer.updateActivity(activityChannel, it));
                    Flux<CustomerStateUpdateReply> engage = sendMessage.thenMany(updateActivity);
                    if (metaUpdate.size() > 0) {
                        return engage
                                .then(customer.updateMetadata(metaUpdate));
                    } else {
                        return engage.then(Mono.just("OK"));
                    }
                })
                .subscribe(
                    message -> log("Successfully processed ussd: " + message),
                    throwable -> log("Failed to process ussd: " + throwable.getMessage())
            );
    };

    private static final NotificationHandler<ReceivedSmsNotification> smsHandler = (notification, customer, appData, responder) -> {
        customer.getMetadata()
                .flatMap(metadata -> {
                    DataValue stepVal = metadata.get("surveystep");
                    DataValue nameVal = metadata.get("name");
                    String step = stepVal == null ? "none" : stepVal.string;
                    String name = nameVal != null ? nameVal.string : null;

                    String input = notification.text;
                    Map<String, DataValue> data = new HashMap<>();
                    String message;
                    ArrayList<Activity> activities = new ArrayList<>();
                    switch (step) {
                        case "initial":
                            if (!input.toLowerCase().contentEquals("yes")) {
                                message = "Respond with YES to start the survey, or ignore this message";
                                break;
                            }
                            message = "On scale of 1 to 10, how likely are you to recommend Banki to friends or family (0 - not likely 10 - very likely?";
                            activities.add(new Activity("UserStartedSurvey", new HashMap<>(), name + "svvv"));

                            data.put("surveystep", DataValue.of("likelihood"));
                            break;
                        case "likelihood":
                            int likelihood = Integer.parseInt(input);
                            if (likelihood < 5) {
                                message = "Why are you not likely to recommend us?";
                            } else {
                                message = "Why are you so likely to recommend us?";
                            }
                            Map<String, String> props = new HashMap<>();
                            props.put("recommendationLikelihood", input);
                            activities.add(new Activity("UserSurveyResponse", props, name + "svvv"));

                            data.put("surveystep", DataValue.of("final"));
                            break;
                        case "final":
                            message = String.format("Thank %s you for your responses. We have deposited KES 100 in your account as reward.", name);
                            Map<String, String> activityProps = new HashMap<>();
                            activityProps.put("recommendationReason", input);
                            activities.add(new Activity("UserSurveyResponse", activityProps, name + "svvv"));
                            activities.add(new Activity("UserCompletedSurvey", new HashMap<>(), name + "svvv"));

                            // coreBanking.deposit(100)

                            data.put("surveystep", DataValue.of("complete"));
                            break;
                        case "complete":
                            message = String.format("Hey %s, you have already completed our survey, Thank you", name);
                            break;
                        default:
                            message = String.format("Hi there, Dial %s to get started!", ussdCode);
                            break;
                    }

                    Mono<CustomerStateUpdateReply> updateMetadata = data.size() > 0 ? customer.updateMetadata(data) : Mono.empty();

                    return updateMetadata
                            .thenMany(Flux.fromIterable(activities).flatMap(it -> customer.updateActivity(activityChannel, it)))
                            .then(customer.replyToMessage(notification.messageId, new Message(new MessageBody(message))));
                }).subscribe(
                    it -> log("Successfully processed sms response: " + it.status),
                    throwable -> log("Failed to process message: " + throwable.getMessage())
                );
    };

    public static void main(String[] args) {
        elarian.setOnReceivedSmsNotificationHandler(smsHandler);
        elarian.setOnUssdSessionNotificationHandler(ussdHandler);
        elarian.connect(new ConnectionListener() {
            @Override
            public void onPending() {}

            @Override
            public void onConnecting() {}

            @Override
            public void onClosed() { }

            @Override
            public void onConnected() { log(String.format("App is running! Dial %s to get started!", ussdCode)); }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
                stop();
            }
        });

        Gson gson = new Gson();

        port(portNumber);

        staticFiles.location("/static");
        get("/login", (req, res) -> {
            HashMap<String, String> data = new HashMap<>();
            data.put("token", elarian.generateAuthToken().block().token);
            data.put("orgId", System.getenv("ORG_ID"));
            return data;
        }, gson::toJson);
    }
}
