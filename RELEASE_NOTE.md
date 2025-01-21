# Release Notes

**Release Notes** of the *Billing Proxy* software:

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
