# Service types in CAxMan
The CAxMan infrastructure stack currently knows three types of CAxMan
services. It is important to understand the differences between those types in
order to choose the right type for a certain use case.

_Important:_ In this document, "service" generally refers to a CAxMan 
service rather than a webservice. A single webservice application may contain
several webmethods which can be used as different types of CAxMan services.

## TLDR (Too Long, Didn't Read)
For the impatient, here are a few general rules of thumb for choosing the right
type of service to develop.

* Make a **synchronous service** only when you can _guarantee_ that it will
  _always_ execute in a time of under 10 seconds.
  
  Do _not_ make a synchronous service if it requires file input or will need to
  do any network communication with other services.
  
  Do _not_ make a synchronous service if you want user interaction or show
  status reports to the user while the service is running.

* Make an **asynchronous service** or **application** when you cannot guarantee
  a short execution time (< 10 seconds). Make an asynchronous service or
  application when you need file input or output or any network communication
  during execution.

  * Make an **asynchronous service** if you want to have status reports
    delivered to the user during execution. These reports can be simple text or
    complex html pages.

    Do _not_ make an asynchronous service if you require user interaction
    during service execution.

  * Make an **application** if you want the user to _interact_ with your service
    during its execution.

Curious what distinguishes the three service types from each other and why the
rules above are as they are? Read on.

## Synchronous services
Synchronous services are the simplest types of CAxMan services. They are
wrappers of single webservice methods which, when called, do something, and
then immediately return their results. They do not require any pre-defined
input or out values.

_Important:_ A synchronous service has to return its response within the HTTP
request timeout time. If it fails to do so, it is automatically terminated by
the workflow manager. This response time is not strictly defined but ranges
typically around 10 to 30 seconds, so synchronous services fit only for simple
tasks. They can be compared to simple command-line commands which take some
input and immediately return some output.

_Hint:_ If your service needs to upload and/or download files via GSS, it most
likely should _not_ be a synchronous service since file upload and download time
depends heavily on file size and the current network speed.

Below you see the execution schema of a synchronous service and its interplay
with the workflow manager during a workflow execution.
<p align="center">
  <img src="service_types_img/sync_service_execution.png"
   alt="Execution diagram of a synchronous service" width="400px"/>
</p>

### Implementation specification

The only requirement for a synchronous service is that it provides a method to
start the service (here, called `startService`).

#### "startService" method (mandatory)

##### Inputs
The inputs of this method depend mainly on the implementation of your service.
However, some optional parameters might be helpful, for example if your service needs
access to the session token, its unique execution ID, or the extra parameters.

The `extraParameters` provide the addresses of some infrastructure components, for example,
 the path to the Workflow Managers SOAP interface.

| Parameter name | Optional | Description |
--- | --- | :--- |
| `sessionToken` | yes | Session token to be used throughout the workflow |
| `serviceID` | yes | The ID assigned to this service by the executione engine, provided automatically |
| `extraParameters` | yes | Needs to be connected to the "extraParameters" workflow input |
| ... |  | |
| `your parameters` | no | |

##### Outputs
The outputs depend exclusively on your service, there are no mandatory outputs. 


## Asynchronous services
Asynchronous services are meant for operations which possibly take longer to
complete than the HTTP request timeout time. These are especially operations
which have file input or output, need to interact with other services over the
network or in general have completion times which depend significantly on their
specific input values. Examples are file converters, meshers, solvers, etc.

Asynchronous services are regularly queried by the workflow manager for their
current status. Via these queries, they can report status reports in the form
of an html page which will be displayed to the user executing the workflow.
These pages can also include images, but they do not allow user interaction.

Below you see the execution schema of a synchronous service and its interplay
with the workflow manager during a workflow execution.
<p align="center">
  <img src="service_types_img/async_service_execution.png"
   alt="Execution diagram of an asynchronous service" width="600px"/>
</p>

### Implementation specification

A asynchronous service has to provide two vital methods: One to start your service
(here, called `startService`) and a `getServiceStatus` method.
The first will, as the name suggests, be used to invoke your service, whereas the
second is used to continuously query your service for its current execution status.

Most importantly, all outputs of the `startService` method have to be defined
as outputs of the `getServiceStatus` method as well, for technical reasons.

Additionally, an asynchronous service may implement the `abortService` method,
to enable the Workflow Manager to gracefully stop your service, as well as the
`notifyService` method, which can be used to send a message to this service, if,
for example, you'd like to signal an event during the execution of this service.
	
#### `startService` method (mandatory)

This method will be called to invoke your service.

##### Inputs

| Parameter name | Optional | Description |
--- | --- | :--- |
| `sessionToken` | yes | Session token to be used throughout the workflow |
| `serviceID` | no | The ID assigned to this service by the execution engine, provided automatically |
| `extraParameters` | yes | Needs to be connected to the "extraParameters" workflow input |
| ... |  | |
| `your parameters` | no | |

##### Outputs

| Parameter name  | Optional | Description |
--- | --- | :--- |
| `status_base64` |  no | The status of your workflow (or a base64-encoded string) |
| ... |  | |
| `your parameters` | no | |


#### `getServiceStatus` method (mandatory)

This method will be called continuously to retrieve the status of your service.

After the output `COMPLETED` has been provided through the parameter
`status_base64`, this method will not be called anymore. Therefore, the final
computation results of your service will need to be provided in the same response,
where `COMPLETED` is returned.

##### Inputs

Though the input parameter `serviceID` may be used to internally identify the
instance of a service.

| Parameter name | Optional | Description |
--- | --- | :--- |
| `sessionToken` | no | Session token to be used throughout the workflow |
| `serviceID` | no | The ID assigned to this service by the execution engine, provided automatically |

##### Outputs
The output parameters of this method have to be identical to the ones of the
`startServce` method, including `status_base64`, which may here have the values `COMPLETED`, `UNCHANGED`, or a base64-encoded string. The value 'UNCHANGED' may be used to signal the WFM that the service's status has not changed since the last request, whereby one can slighty reduce traffic and load for the WFM (especially for large status descriptions). In this case the WFM will simply forward the previous status to the portal.
	
#### `notifyService` method (optional)

If provided, this method can be used to pass a message to a running instance
of your service.

##### Inputs

| Parameter name | Optional | Description |
--- | --- | :--- |
| `sessionToken` | no | Session token to be used throughout the workflow |
| `serviceID` | no | The ID assigned to this service by the execution engine, provided automatically |
| `message` | no | The message you'd like to pass to your service |


##### Outputs

| Parameter name  | Optional | Description |
--- | --- | :--- |
| `response` | no | The response of your service to the provided message |

	
#### `abortService` method (optional)

If provided, this method will be called by the Workflow Manager to gracefully
stop your service.

##### Inputs

| Parameter name | Optional | Description |
--- | --- | :--- |
| `sessionToken` | no | Session token to be used throughout the workflow |
| `serviceID` | no | The ID assigned to this service by the execution engine, provided automatically |


##### Outputs

| Parameter name  | Optional | Description |
--- | --- | :--- |
| `result` | no | Was the attempt to abort the service successful? (`true` or `false`) |

## Applications
Applications are very similar to asynchronous services, but with a slightly
different scope. While an asynchronous service, once started, runs without any
further user input (it only shows output to the user regularly), applications
are designed to interact with the user while they are being executed. Instead
of being queried for their status by the workflow manager, they have to
actively report to the workflow manager once their execution is finished.

Below you see the execution schema of an application and its interplay
with the workflow manager during a workflow execution.
<p align="center">
  <img src="service_types_img/application_execution.png"
   alt="Execution diagram of an application" width="600px"/>
</p>

### Implementation specification
Applications merely need to provide a SOAP method to be started by.

However, once their execution is finished they need to call the Workflow
Manager's SOAP method `serviceExecutionFinished` to signal that they have
finished their execution.
The path to the Workflow Manager's SOAP interface can be retrieved from
the extraParameters, which provides a string of ","-separated key-value pairs, e.g. : 
``
WFM=https://caxman.clesgo.net/dfki/tomcat/WorkflowManager2Service/services/SOAP,phpFileChooser=https://caxman.clesgo.net/portal/filechooser/,
``

The Workflow Manager can be identified by the key `WFM`.

Similar to the asynchronous services, the parameters provided to the 
`serviceExecutionFinished` method of the Workflow Manager have to be identical
to the output parameters of your `startService` method.

#### `startService` method (mandatory)

This method will be called to invoke your service.

##### Inputs

| Parameter name | Optional | Description |
--- | --- | :--- |
| `sessionToken` | no | Session token to be used throughout the workflow |
| `serviceID` | no | The ID assigned to this service by the execution engine, provided automatically |
| `extraParameters` | no | Needs to be connected to the "extraParameters" workflow input |
| ... |  | |
| `your parameters` | no | |

##### Outputs

| Parameter name  | Optional | Description |
--- | --- | :--- |
| `status_base64` |  no | The status of your workflow (a base64-encoded string) |
| ... |  | |
| `your parameters` | no | |

#### Workflow Manager's `serviceExecutionFinished` method (call mandatory)
Your application needs to call this method once it has completed its execution.

The result parameters (identical to the output parameters of your `startService` method)
need to be base64-encoded and passed trough the `xmlOutputs_base64` parameter, for example:

Raw:
``<ServiceOutputs><status_base64>dXNlciBjb250aW51ZWQ=</status_base64></ServiceOutputs>``
 
Base64-encoded:
``PFNlcnZpY2VPdXRwdXRzPjxzdGF0dXNfYmFzZTY0PmRYTmxjaUJqYjI1MGFXNTFaV1E9PC9zdGF0dXNfYmFzZTY0PjwvU2VydmljZU91dHB1dHM+``

The name of the upper level tag (here, `ServiceOutputs`) may be arbitrary.

##### Inputs

| Parameter name | Optional | Description |
--- | --- | :--- |
| `sessionToken` | no | Session token to be used throughout the workflow |
| `serviceID` | no | The ID assigned to this service by the execution engine, provided automatically |
| `xmlOutputs_base64` | no | Base64-encoded result parameters of your service |


The parameters provided through the parameter `xmlOutputs_base64` have to be identical to the ones of the
`startService` method, including `status_base64`, which may here have the values `COMPLETED`, `UNCHANGED`, or a base64-encoded string. The value 'UNCHANGED' may be used to signal the WFM that the service's status has not changed since the last request, whereby one can slighty reduce traffic and load for the WFM (especially for large status descriptions). In this case the WFM will simply forward the previous status to the portal.

##### Outputs

| Parameter name  | Optional | Description |
--- | --- | :--- |
| `success` |  no | Whether the Workflow Manager successfully registered that your service has finished |

