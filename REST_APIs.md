# Billing Scheduler

**Version:** 2.0.1  
**Description:** Swagger REST APIs for the billing-scheduler software  


## REST API Endpoints

### billing-scheduler-controller
| Verb | Path | Task |
|------|------|------|
| POST | `/billingScheduler/validateProductOfferingPrice` | validateProductOfferingPrice |
| POST | `/billingScheduler/start` | startScheduler |

### info-scheduler-controller
| Verb | Path | Task |
|------|------|------|
| GET | `/scheduler/info` | getInfo |
| GET | `/scheduler/health` | getHealth |

