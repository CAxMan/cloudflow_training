# Tutorial: Creating a simple asynchronous service
In this tutorial, you will take an existing piece of software (in this case,
it's a very simply Python script which doesn't do anything else but to wait
for a while) and wrap a simple asynchronous service around it, so that the
software can be used on the CAxMan infrastructure stack. In a more
realistic scenario, the software would be anything which takes a longer time
to complete and where intermediate status updates should be displayed to the
user during service execution.

## Step 1: Prepare the example code
This tutorial starts from the code example
[async_waiter_tutorial](../../code_examples/Python/async_waiter_tutorial). To
begin with, copy this folder to a new location or alternatively create a new,
local git branch to work on.

Now, change into the folder containing your copy of the code example and open a
terminal there.

### Adapt the webservice's context root
As for the [calculator-service tutorial](python_sync_calculator.md), the first
thing to do is to adapt
the existing code so that it can run on your deployment setup. To be able to
listen to the correct http requests, the webservice needs to know its _relative
deployment path_ or _context root_.

Example: If you aim to run the service on a VM which is reachable via
`<somehost>/mycompany/myvm` (`<somehost>` can, for example, be
`caxman.clesgo.net`), the context root needs to be set to `/mycompany/myvm`.

The code example we're working with is a Docker container which is configured
via environment variables defined in the `env` file in the source folder. Edit
this file and change the context-root definition to the string appropriate to
your deployment path.

## Step 2: Code overview
The example directory already contains a full service skeleton and a Docker
container to wrap the service in. For now, have a look at
`app/wait_a_while.py`, which is our placeholder for a long-running complex
calculation. The script expects three input parameters when executed: the
number of seconds to wait, the path to a statusfile, and the path to a results
file. In that respect, this script is not different from many "real" software
packages: It expects user input (how many seconds to wait), it will create log
files (the status file) during execution and results once done (the results
file).

The main part of the pre-implemented webservice skeleton is `app/Waiter.py`.
Open that file in a text editor and have a quick look around. You will see an
almost empty definition of a `WaiterService` class as well as a function
`create_html_progresspage()`, which takes a number in percent and creates a
very simple html status page from it.

In the following, we will add two webmethods to the so far empty
`WaiterService` class: one to start the asynchronous service, and one to obtain
its current exection status. Both these functions will be called by the
workflow manager when the webservice is registered as a CAxMan asynchronous
service.

## Step 3: Add a start method to the webservice skeleton
Before we implement the method to start the waiter service, let's think shortly
about the method interface. Every asynchronous service needs to accept at least
two input parameters: The `serviceID` (a unique identifier assigned by the
workflow manager) and the `sessionToken` (an authentication token that can be
used to verify user credentials). In this example, we also want to have the
number of seconds to wait as an input, meaning that we will implement a total
of three input parameters.

Furthermore, every asynchronous service at least needs to have one output
parameter, namely `status_base64`, a base64-encoded status string.
Additionally, we want to have another output parameter representing the result
of the long- running program (= the waiter in this case) we start from within
the service.

With this in mind, add the following method definition to the `WaiterService`
class:
```python
    @spyne.srpc(Unicode, Unicode, Integer, _returns=(Unicode, Unicode),
                _out_variable_names=("status_base64", "result"))
    def startWaiter(serviceID, sessionToken, secondsToWait=300):
```
The function decorator `@spyne.srpc(...)` marks the following function
definition as a Spyne SOAP method, which will be made available via the
webservice's wsdl file. In this decorator, we define the function signature,
specifying two strings (`Unicode`) and one integer input value as well as two
string output values. The input values map directly to the arguments of the
function definition in the next line (`serviceID`, `sessionToken`, and
`secondsToWait`). Since in Python, return arguments are never named, we also
explicitly define the names the return variables will have in the SOAP service
definition.

Our start method needs to perform three tasks:
1. Prepare a unique environment (meaning a location for input, status, and
   output data) for the waiter script to run in.
2. Start the waiter script.
3. Create a first status report to send back to the workflow manager as a
   return argument.

_Important:_ Note that a CAxMan service can be run several times in parallel.
It is therefore important that subsequent status-query calls to the service
return information from the correct long-running background process (the waiter
script in this example). The workflow manager assigns a unique service ID to
every service which is executed (the service ID is really more a
_service-execution ID_), which we can use to distinguish between these
different service executions.

Add the following lines to the method you just created:
```python
        waiterdir = os.path.join(WAITER_LOG_FOLDER, serviceID)
        if not os.path.exists(waiterdir):
            os.mkdir(waiterdir)
        statusfile = os.path.join(waiterdir, 'status.txt')
        resultfile = os.path.join(waiterdir, 'result.txt')
```
We create a temporary folder named after the service ID (`WAITER_LOG_FOLDER`
is read from an environment variable at the top of `Waiter.py`) to have a
unique environment for the waiter script to run in. We furthermore define paths
for the status and results files.

Now, we add the execution of the waiter script to the function:
```python
        command = ['python', 'wait_a_while.py', str(secondsToWait),
                   statusfile, resultfile]
        subprocess.Popen(command)
```
These lines start the waiter script `wait_a_while.py` as a detached subprocess.
It is crucial here that the `startWaiter` method does _not_ wait for this
process to return, as this would ruin the idea of an asynchronous service.
Instead, the `subprocess.Popen()` call returns immediately.

Finally, add the following lines to conclude the `startWaiter` method:
```python
        status = base64.b64encode(create_html_progresspage(0))
        result = "UNSET"

        return (status, result)
```
We call `create_html_progresspage()` with an initial progress of 0 % (which is
our "best guess" since we actually don't know the status yet). We then
base64-encode the result page to make it digestable for the workflow manager.
We furthermore set the result value to some value indicating that it is not
assigned yet. (We have to supply this output value, but it won't have any
meaningful content as long as the waiter script is still running.)

_Note:_ The status page we return can be anything from a very simple text to a
full-fledged html page including, for example, images. This way, we can create
a rich feedback for the user during the execution of asynchronous services.

## Step 4: Implement getServiceStatus()
The second function missing in our service has the pre-defined name
`getServiceStatus()`. This function is called every few seconds by the workflow
manager to query the current execution status of the service, until the service
terminates.

Add the following function definition to the `WaiterService` class:
```python
    @spyne.srpc(Unicode, Unicode, _returns=(Unicode, Unicode),
                _out_variable_names=("status_base64", "result"))
    def getServiceStatus(serviceID, sessionToken):
```
The function signature is similar to that of the `startWaiter` method. The two
input arguments `serviceID` and `sessionToken` are again mandatory for
asynchronous services. Note that the output arguments are exactly identical to
those of the start method.

First, our function needs to make sure to query the correct background waiter
script. Therefore, add the following lines to the function:
```python
        waiterdir = os.path.join(WAITER_LOG_FOLDER, serviceID)
        statusfile = os.path.join(waiterdir, 'status.txt')
        resultfile = os.path.join(waiterdir, 'result.txt')
```
You can see that this again creates directory and file names using the unique
service ID, just as we did in the `startWaiter` method.

Next, the function should read the status file (which is periodically updated
by the waiter script) and act depending on its content. Add the following lines
to your code:
```python
        with open(statusfile) as f:
            current_status = f.read().strip()
```

The waiter script simply writes a number between 0 and 100 into the status file
to indicate its progress, so we can use this number to decide how to proceed.
We first add the logic for when the waiter script has finished its execution:
```python
        if current_status == "100":
            status = "COMPLETED"
            # Read result page from waiter
            with open(resultfile) as f:
                result = base64.b64encode(f.read())
            return (status, result)
```
In this case, we report the pre-defined status string `"COMPLETED"`, which
will tell the workflow manager that the service has finished and that the next
element in the workflow can now be executed. We furthermore read the waiter
script's result file (which should at this state be filled with something
meaningful), base64-encode it and send it back to the workflow manager together
with the status string.

Finally, we add the logic for when the waiter script has _not_ yet finished its
work:
```python
        result = "UNSET"
        status = base64.b64encode(create_html_progressbar(int(current_status)))
        return (status, result)
```
In that case, we use the current status value to again create a html status
page and once again return "UNSET" as the result output.

### Return values of `startWaiter` and `getServiceStatus`
You might have noticed an oddity about the return values we defined for the two
methods we implemented. Specifically, we defined the `result` return value in
`startWaiter` without actually using it for anything meaningful. The reason for
this is simple: The workflow manager will use the main method of an 
asynchronous service (here, that is `startWaiter`) to deduce the service's 
input and output arguments. So any output value which should be used later on
in the workflow _has to be defined in this main method_. Since the final result
of an asynchronous service cannot be known at the execution time of this main
method, the last call to `getServiceStatus` has the responsibility to deliver
the final value for all output arguments.

### The status string `"UNCHANGED"`
In our implementation of `getServiceStatus`, the status we report is either a
base64-encoded html page (when the service is still running) or the string
`"COMPLETED"` in case the service has finished its work. The workflow manager
can process a second pre-defined status string, namely `"UNCHANGED"`. This
status means that there was no change in status compared to the previous call
to `getServiceStatus`, and the workflow manager will continue showing the last
status page created. This is useful in situations where the creation of a
status page itself is costly, for example when a lot of log processing, image
creation etc. needs to be done. In our simple example here, we omitted this
status option for the sake of brevity.

## Step 5: Deploy and test the waiter webservice
Your waiter webservice is now complete and ready for deployment.
If you were not sure about some of the code additions, you can compare your 
code with the code example
[async_waiter](../../code_examples/Python/async_waiter), which contains the
waiter service in its complete form. (It contains actually a bit more than the
implementation in this tutorial, but you can ignore those differences here.)

As in tutorial 3-1, we use a pre-defined build script for deployment:
```bash
./rebuildandrun.sh <port>
```
The service will be started in a Docker container named `waiter`. Again, you
can run `docker ps` and `docker logs waiter` to check that the service is
running properly.

For making test calls to the service, the code again ships with a test client
available in the `test_client/` folder. To run the test client as a Docker 
container, run:
```bash
./build.sh
./run.sh <port> start
```
The calls the `startWaiter` method with a dummy service ID and session token,
which should give you a similar output to this:
```
Using port 8080
wsdl URL is http://localhost:8080/sintef/docker_services/waiter/Waiter?wsdl
Starting service
(reply){
   status_base64 = "PGh0bWw+CjxoZWFkPgo8dGl0bGU+V2FpdGVyIHN0YXR1czwvdGl0bGU+CjwvaGVhZD4KPGJvZHkgc3R5bGU9Im1hcmdpbjogMjBweDsgcGFkZGluZzogMjBweDsiPgo8aDE+V2FpdGVyIHN0YXR1cyBhdCAyMjoyMzoxMTwvaDE+CjxkaXYgc3R5bGU9ImJvcmRlci1yYWRpdXM6IDVweDsgYm9yZGVyLWNvbG9yOiBsaWdodGJsdWVibHVlOyBib3JkZXItc3R5bGU6ZGFzaGVkOyB3aWR0aDogODAwcHg7IGhlaWdodDogODBweDtwYWRkaW5nOjA7IG1hcmdpbjogMDsgYm9yZGVyLXdpZHRoOiAzcHg7Ij4KPGRpdiBzdHlsZT0icG9zaXRpb246IHJlbGF0aXZlOyB0b3A6IC0zcHg7IGxlZnQ6IC0zcHg7IGJvcmRlci1yYWRpdXM6IDVweDsgYm9yZGVyLWNvbG9yOiBsaWdodGJsdWU7IGJvcmRlci1zdHlsZTpzb2xpZDsgd2lkdGg6IDAuMHB4OyBoZWlnaHQ6IDgwcHg7cGFkZGluZzowOyBtYXJnaW46IDA7IGJvcmRlci13aWR0aDogM3B4OyBiYWNrZ3JvdW5kLWNvbG9yOiBsaWdodGJsdWU7Ij4KPGgxIHN0eWxlPSJtYXJnaW4tbGVmdDogMjBweDsiID4wJTwvaDE+CjwvZGl2Pgo8L2Rpdj4KPC9oZWFkPgo8L2JvZHk+"
   result = "UNSET"
 }
```
You see the base64-encoded status page as well as the still unset result.

Now, execute the run script again, but with a different argument:
```bash
./run.sh <port> status
```
This will call the `getServiceStatus` method, which should return output
similar to the following:
```
Using port 8080
wsdl URL is http://localhost:8080/sintef/docker_services/waiter/Waiter?wsdl
Calling getServiceStatus:
(reply){
   status_base64 = "PGh0bWw+CjxoZWFkPgo8dGl0bGU+V2FpdGVyIHN0YXR1czwvdGl0bGU+CjwvaGVhZD4KPGJvZHkgc3R5bGU9Im1hcmdpbjogMjBweDsgcGFkZGluZzogMjBweDsiPgo8aDE+V2FpdGVyIHN0YXR1cyBhdCAyMjoyMzoyODwvaDE+CjxkaXYgc3R5bGU9ImJvcmRlci1yYWRpdXM6IDVweDsgYm9yZGVyLWNvbG9yOiBsaWdodGJsdWVibHVlOyBib3JkZXItc3R5bGU6ZGFzaGVkOyB3aWR0aDogODAwcHg7IGhlaWdodDogODBweDtwYWRkaW5nOjA7IG1hcmdpbjogMDsgYm9yZGVyLXdpZHRoOiAzcHg7Ij4KPGRpdiBzdHlsZT0icG9zaXRpb246IHJlbGF0aXZlOyB0b3A6IC0zcHg7IGxlZnQ6IC0zcHg7IGJvcmRlci1yYWRpdXM6IDVweDsgYm9yZGVyLWNvbG9yOiBsaWdodGJsdWU7IGJvcmRlci1zdHlsZTpzb2xpZDsgd2lkdGg6IDIyNC4wcHg7IGhlaWdodDogODBweDtwYWRkaW5nOjA7IG1hcmdpbjogMDsgYm9yZGVyLXdpZHRoOiAzcHg7IGJhY2tncm91bmQtY29sb3I6IGxpZ2h0Ymx1ZTsiPgo8aDEgc3R5bGU9Im1hcmdpbi1sZWZ0OiAyMHB4OyIgPjI4JTwvaDE+CjwvZGl2Pgo8L2Rpdj4KPC9oZWFkPgo8L2JvZHk+"
   result = "UNSET"
 }
```

After 60 seconds (which is the waiting time pre-defined by the test client),
yet another call to `./run.sh <port> status` should give the following 
output:
```
Using port 8080
wsdl URL is http://localhost:8080/sintef/docker_services/waiter/Waiter?wsdl
Calling getServiceStatus:
(reply){
   status_base64 = "COMPLETED"
   result = "FINISHED"
 }
```

## Step 6: Register the waiter service as a CAxMan service
You are now ready to register the deployed waiter service as a CAxMan 
asynchronous service.

First, make sure that the webservice's wsdl is reachable from the outside by
opening the following URL in a browser:
```
https://<host><your_context_root>/waiter/Waiter?wsdl
```
Replace `<host>` and `<your_context_root>` with the appropriate values. You
should receive an xml file containing a formal description of the webservice.

Now, register the webservice's `startWaiter` method as a CAxMan service
using the workflow-editor GUI. See the tutorial on [service registration](../workflows/basics_service_registration.md) for details on how to do that. Make
sure that you select _asynchronous service_ during the registration.

You can now create a simple workflow which contains only your newly created
waiter service. Make sure to connect the workflow inputs `serviceID` and
`sessionToken` to the corresponding inputs of the waiter service, and connect
the waiter service's `result` output to the workflow output. Hard-code a value
for the `secondsToWait` input of the waiter service.

When you save, publish, and run your workflow, you should see a very simple
status page updating every few seconds until the service has waited for the
time you specified in the workflow.
