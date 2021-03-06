## Pure PWM
The idea here is to use only GPIO to drive a servo. Extra board like the PCA9685 would not be required.

So far..., there is a problem

It works from the shell:
```bash
#!/usr/bin/env bash
#
# pin GPIO_18 is #12
#
gpio -g mode 18 pwm
gpio pwm-ms
gpio pwmc 192
gpio pwmr 2000
gpio -g pwm 18 150
sleep 1
gpio -g pwm 18 200
echo Done.
```

It woks from C (with wiringPi):

```c
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <wiringPi.h>

int main (void) {
   fprintf(stdout, "Raspberry Pi PWM wiringPi test program\n");
   wiringPiSetupGpio();
   pinMode (18, PWM_OUTPUT); // Pin #12
   pwmSetMode (PWM_MODE_MS);
   pwmSetRange (2000);
   pwmSetClock (192);
   pwmWrite(18, 150);
   delay(1000);
   pwmWrite(18, 200);
   fprintf(stdout, "Done\nll
   ./pwm    ");
   return 0;
}
```

But not from PI4J :(
```
PWM Control - pin 01 ... started.
Exception in thread "main" com.pi4j.io.gpio.exception.UnsupportedPinModeException: This GPIO pin [GPIO 1] does not support the pin mode specified [soft_pwm_output]
	at com.pi4j.io.gpio.GpioProviderBase.export(GpioProviderBase.java:115)
	at com.pi4j.io.gpio.WiringPiGpioProviderBase.export(WiringPiGpioProviderBase.java:90)
	at com.pi4j.io.gpio.impl.GpioPinImpl.export(GpioPinImpl.java:158)
	at com.pi4j.io.gpio.impl.GpioControllerImpl.provisionPin(GpioControllerImpl.java:565)
	at com.pi4j.io.gpio.impl.GpioControllerImpl.provisionPin(GpioControllerImpl.java:538)
	at com.pi4j.io.gpio.impl.GpioControllerImpl.provisionPin(GpioControllerImpl.java:533)
	at com.pi4j.io.gpio.impl.GpioControllerImpl.provisionSoftPwmOutputPin(GpioControllerImpl.java:866)
	at com.pi4j.io.gpio.impl.GpioControllerImpl.provisionSoftPwmOutputPin(GpioControllerImpl.java:876)
	at pwm.Pwm01.main(Pwm01.java:21)
```

... Working on it.
