# Banki

>
> Awesome Bank
>
> 

## Usage

```
./gradlew run # watch out for environment variables :)
```

## Features

- Register
- Check Balance
- Withdraw
- Survey

## Interfaces

### USSD

```
# New User
Welcome to Banki
1 - Register
2 - Quit

## Register
Awesome, what is your name?
[]
Welcome Joey! We are processing your application and will get back to you shortly.  ** an SMS will be sent

## Quit
Bye bye, see you next time.

# Existing User
Hey Joey! Welcome to Banki
1 - Check Balance
2 - Withdraw
3 - Quit

## Balance
Your Balance is KES 134.

## Withdraw
-
How much do you need? You can withdraw up to KES 134
[]
We are processing your application and will get back to you shortly. ** an SMS will be sent

-
You do not have enough for a withdrawal

## Quit
Bye Joey, See you next time.



SMS Survey
Thankf you for using Banki. To server you better, we would like to run a short survery. Reposnd with Yes to continue.


On ascale of 1 to 10, how llikely are you to rcommend Banki to friend or familly(0 - notlikely 10-very likely?
[]
Why are [so|not] likely to recomend us ** < 5 > 5
[]
Thank you for your responses. We have deposited KES 100 in your account as reward.


<<<<Activities>>>>
UserRegistered{}
UserCheckedBalance{balance}
UserWithdrew{amount}
UserStartedSurvey{}
UserSurveyResponse{...}
UserCompletedSurvey{}
```
