# VisualVM Setup

VisualVM is an open-source profiler, which provides detailed information about Java applications while they are running on the Java Virtual Machine (JVM). VisualVM's graphical user interface enables you to quickly and easily see information about multiple Java applications, monitoring usage of CUP, memory, threads, and .etc. It provides visual interface for local and remote Java applications, and support features for Thread/Heap dumps to save essential dump nodes for later investigation.

(This document content is referred to : <https://www.tutorialspoint.com/intellij_idea/intellij_idea_profiling.htm>)

- [VisualVM Setup](#visualvm-setup)
  - [VisualVM setup](#visualvm-setup-1)
  - [Monitoring application running locally](#monitoring-application-running-locally)
  - [Monitoring an application running on a K8s pod](#monitoring-an-application-running-on-a-k8s-pod)
  - [Tips for using VisualVM](#tips-for-using-visualvm)


## VisualVM setup

1. Download VisualVM from [here](https://visualvm.github.io/download.html).
2. Extract the zip file to local.
3. Navigate to etc/visualvm.conf file and update/add the following line in this file (*etc* folder can be found in the unzipped VisualVM directory) −
```
   visualvm_jdkhome=<path of JDK>
```
   - If your JDK is installed in the C:\Program Files\Java\jdk-11.0.13 directory then it should look like this −
```
   visualvm_jdkhome="C:\Program Files\Java\jdk-11.0.13"
```
   - To check your current Java JDK directory: 
     - From Windows **Start**, search keywords and click **edit environment variables for your account**; 
     - In the **Environment Variables** window, check the **JAVA_HOME** value under the **System variables** table.

   - OR using powershell/bash command to retrive JDK directory:
   ```
   > $env:JAVA_HOME
   ```
## Monitoring application running locally

1. Double-click on the bin/visualvm.exe file to start VisualVM
2. Start Java applications to be monitored, e.g. game server or run script ./start.ps1
3. Select the Java applications to be monitored from left pane, then start the profiling investigation in the right pane. 

## Monitoring an application running on a K8s pod

The Minecraft Servers start up with
[JMX](https://www.baeldung.com/java-management-extensions) enabled. This exposes
a local port (`1089`) inside the pod that a VisualVM instance can use to monitor
the application in the same way as if said application was running locally.

Since the JMX port is only visible locally inside the pod we need to tell out
local `kubectl` to forward the `1089` port from the pod to our local machine. 

```bash
kubectl port-forward <pod-name> 1089:1089
```

Finally, we can open VisualVM and connect to the remote JVM:

1. Right click on `Local` in the left pane and select `Add JMX Connection`
   - For `Connection` put `localhost:1089`

Double click the new item in the sidebar and give it some seconds to load. After
that you should be able to use VisualVM as you would to monitor a local process.
Note that running VisualVM on your local machine **will** affect the performance
of the application you are monitoring.

You can use this same instructions to monitor multiple pods at the same time,
you'll only need to make sure to use different `ports` for each of them on your
local machine.

Note: if you stop the port-forwarding, VisualVM will no longer be able to
monitor the application. So you should keep the port-forwarding process running
for as long as you want to monitor the application.

## Tips for using VisualVM 

- Find Java application's working directory from **Overview** tag:

![directory](https://user-images.githubusercontent.com/73333032/193342933-0d24a1b6-d3d5-47c1-998c-0f61abbf32c3.png)

- Using **Monitor** tag to monitor resource usage:

![monitor](https://user-images.githubusercontent.com/73333032/193343156-bff01ae9-5187-40e3-80d6-212a0e0b655c.png)

- Profile specific classes live in **Profiler** tag to observe target objects allocation and deallocation: (e.g. below is profiling " *com.microsoft.*** ") 

![profiler](https://user-images.githubusercontent.com/73333032/193343229-b022781c-3b57-461d-8b8c-981782a23892.png)

- **Snapshot** of **Profiler** tag facilitates more detailed stack trace analysis:

![snapshot](https://user-images.githubusercontent.com/73333032/193343299-2eb45297-187e-445c-b050-705fe3f0c929.png)

