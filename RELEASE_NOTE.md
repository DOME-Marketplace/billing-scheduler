# Release Notes

**Release Notes** of the *Billing Scheduler* software:

### <code>0.1.0</code> :calendar: 01/04/2025
**Improvements**
* Usage of `2.0.0` version of `Brokerage Utils`.


### <code>0.0.13</code> :calendar: 17/03/2025
**BugFixing**
* Verify if `products` is not **null** (jump validation checks in TMForum SDK).


### <code>0.0.12</code> :calendar: 07/03/2025
**BugFixing**
* Usage of `2.0.0` version for all `TMForum SDK`.
* Add check for the `priceType` if it's **null** (jump validation checks in TMForum SDK).


### <code>0.0.11</code> :calendar: 05/03/2025
**BugFixing**
* Usage of `2.0.0` version of `tmf637-v4` - commented all `validateJsonElement` methods.


### <code>0.0.10</code> :calendar: 03/03/2025
**BugFixing**
* Update `tmf637-v4` dependency to `1.0.3` version.


### <code>0.0.9</code> :calendar: 03/03/2025
**Improvements**
* Include exception handling with `ControllerExceptionHandler.java` class.

**BugFixing**
* Update `tmf637-v4` dependency to `1.0.2` version.


### <code>0.0.8</code> :calendar: 28/02/2025
**Improvements**
* Add `StartupListener` listener to log (display) the current version of *Billing Scheduler* at startup.

**BugFixing**
* Update `tmf637-v4` dependency to `1.0.1` version.


### <code>0.0.7</code> :calendar: 03/02/2025
**BugFixing**
* Add `validation` dependency.
* Set `org.apache.coyote.http11: ERROR` to avoid the `Error parsing HTTP request header`.


### <code>0.0.6</code> :calendar: 31/01/2025
**Improvements**
* Add `StartRequestDto` for post call to start the scheduler **manually**.
* Usage of the `BILLING_PREFIX` in the `application.yaml` file.


### <code>0.0.5</code> :calendar: 21/01/2025
**Improvements**
* Improvement of TMForum APIs for internal path (i.e. remove tmf-api/productCatalogManagement/v4 if it set TMF_ENVOY to `false`.
* Usage of `billing-proxy` endpoint instead of `billing-engine`.

**Feature**
* Add API `/scheduler/start` to start the billing process via POST request: `payload { "datetime": "2025-01-11T13:14:33.213Z" }`.
 

### <code>0.0.4</code> :calendar: 16/01/2025
**Improvements**
* Add `apiProxy` settings via **environment variables**. Set TMF_ENVOY to `true`, TMF_NAMESPACE, TMF_POSTFIX, and TMF_PORT to apply it.
 

### <code>0.0.3</code> :calendar: 20/12/2024
**Feature**
* Verify if there are product to start the bill process.
* Invoke `invoicing-service` and `billing-engine` services to get the bill objects.
* Save billing (`AppliedCustomerBillingRate`) in the **TMForum APIs**. 


### <code>0.0.2</code> :calendar: 11/11/2024
**Feature**
* Add swagger UI for REST APIs.

**Improvements**
* Usage of **BuildProperties** to get info from `pom.xml` instead of from the `application.yaml`.


### <code>0.0.1</code> :calendar: 25/09/2024
**Feature**
* Init project.
