#include <Wire.h>
#include <Servo.h>
#include <LiquidCrystal_I2C.h>


Servo myservo;
Servo myservo1;
LiquidCrystal_I2C lcd(0x27, 20, 4);



int pin = 2;
int pin1 = 7;
int value = 0;
int value1 = 0;
int pirState = LOW;
int irState = LOW;

void setup() {
  Serial.begin(9600);

  pinMode(pin, INPUT);
  pinMode(pin1, INPUT);

  myservo.attach(8);
  myservo1.attach(9);
  lcd.init();
  lcd.backlight();

  lcd.setCursor (2, 0);
  lcd.print("NASA Space  Apps");

  lcd.setCursor (3, 1);
  lcd.print("Challenge 2021");

  lcd.setCursor (3, 2);
  lcd.print("Team ShockWave");

  lcd.setCursor (2, 3);
  lcd.print("I am Vitamin-Sea");

}

void loop() {

  value = digitalRead(pin);

  value1 = digitalRead(pin1);

  if (value == HIGH) {


    if (irState == LOW) {

      irState = HIGH;


      myservo1.write(50);
      myservo.write(0);
      delay(200);
      myservo.write(20);
      Serial.println("r");
      delay(500);
      Serial.println("r");


    }
  } else {


    if (irState == HIGH) {
      irState = LOW;
    }
  }

  if (value1 == HIGH) {


    if (pirState == LOW) {
      Serial.println("m");
      pirState = HIGH;
    }
  } else {


    if (pirState == HIGH) {
      pirState = LOW;
    }
  }

  if (Serial.available() > 0)
  {
    char data = Serial.read();
    Serial.print(data);
    if (data == 'b')
    {
      myservo1.write(140);



    }
  }
}
