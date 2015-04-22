# NettyTest

The test task on Netty framevork

The server consists of sutch classes:

>HttpNettyServer.java - main class

>HttpNettyServerInitializer.java - pipeline initializer

>DuplexStatisticsHandler.java - handler for statistics gathering

>RequestHandler.java - processes Hello World and redirects

The other 2 classes is not involved yet:

>ChannelSpeedCheckHandler.java - handling chanel statistics (not working properly yet)

>StatisticsRequestHandler.java - forget to delete.

Results of ab test run: 

```sh
ab -c 100 -n10000 127.0.0.1:8080/status
This is ApacheBench, Version 2.3 <$Revision: 655654 $>
Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
Licensed to The Apache Software Foundation, http://www.apache.org/

Benchmarking 127.0.0.1 (be patient)

Completed 1000 requests
Completed 2000 requests
Completed 3000 requests
Completed 4000 requests
Completed 5000 requests
Completed 6000 requests
Completed 7000 requests
apr_poll: The timeout specified has expired (70007)
Total of 7050 requests completed
```