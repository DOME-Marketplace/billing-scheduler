# Billing Scheduler

## Description
Billing Scheduler task in brokerage for DOME project.

## How to Run Application

**Start the application using these commands below**

> [!TIP] 
> Run these commands inside the root folder of this project; i.e inside the **billing-scheduler** folder.


- **Using maven** 
  ```
  mvn spring-boot:run
  ```

- **From jar file**
  Create a jar file using `mvn clean install` command and then execute:
  ```
  java -jar target/billing-scheduler-<VERSION>-SNAPSHOT.jar
  ```

> [!NOTE]  
> By default spring boot application starts on port number 8080. If port 8080 is occupied in your system then you can change the port number by uncommenting and updating the **server.port** property inside the **application.yaml** file that is available inside the **src > main > resources** folder.


## How to Run Unit Test Cases

**Run the test cases using this command below**

> [!TIP] 
> This command needs to run inside the root folder of this project i.e inside the **billing-scheduler** folder

- **To run all the test cases**
  ```
  mvn test
  ```

