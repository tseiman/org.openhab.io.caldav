

# org.openhab.io.caldav
A CalDav IO implementation for openHAB

<hr />
<img src="https://raw.githubusercontent.com/tseiman/org.openhab.persistence.caldav/master/images/CalDavBinding.png" alt="CalDav openHAB binding" style="width:400;">


## Content
- [Introduction] (#introduction)
- [Thanks] (#thanks)
- [Notice] (#notice)
- [Compatibility] (#compatibility)
- [Preconditions] (#preconditions)
- [Install] (#install)
- [openhab.cfg Example] (#openhabcfg-example)
- [Calendar Event Configuration] (#calendar-event-configuration)
- [Solving caldav IO errors] (#solving-caldav-io-errors)


## Introduction
CalDav IO binding implements the same functionality as the openHAB GCal binding but connects 
instead to Google Calendar to any CalDAV enabled calendar server. At the moment it is used in a very similar 
way as the GCal binding.

CalDav IO allows you to query events from CalDAV calendar and schedule those to openHAB scheduler.


## Thanks
--> to all who have contributed to [GCal](https://github.com/openhab/openhab/wiki/GCal-Binding) IO, as this plugin is following GCal implementation and configuration

## Notice
- CalDAV IO binding is beta and just tested on openHAB 1.8, on a few systems and needs more review
- CalDAV IO has as well a persistence part: [org.openhab.persistence.caldav] (https://github.com/tseiman/org.openhab.persistence.caldav)

## Compatibility
- openHAB 1.8 (tested)
- java 1.7
- following calendar Servers have been tested:
  - [radicale](http://radicale.org) (tested, doesn't implement full required function set, however it might work)
  - [davical](http://www.davical.org) (tested, fully compatible)

Please contact over openHAB Google group if you like to share your testing experience.

## Preconditions
- You have installed a working calendar server such as
  - [davical](http://www.davical.org) (tested)
  - [bedework](https://www.apereo.org/projects/bedework) (un-tested)
  - or Apple's [Calendar Server](http://calendarserver.org) (un-tested)
- You are able to access this calendar over http(s) via any kind of CalDAV application
- in case of using certificates (recommended) you have server certificate and eventually CA certificate at hand
- You have downloaded and configured org.openhab.io.caldav binding
- run openHAB eventually in debug mode to see the persistence service working
- the user you'll configure to `caldav` will need read/write rights to the CalDAV collection/calendar on CalDAV server

## Install
At the moment there are pre-compiled exports from eclipse available [download org.openhab.io.caldav_XXXX.jar] (https://raw.githubusercontent.com/tseiman/org.openhab.io.caldav/master/build/org.openhab.io.caldav_1.8.0.201507310940.jar) and place it in the `OPENHAB_ROOT/addons` folder.

## Configure
Following configuration entries are supported for openhab.cfg:

<table>
<tr><th><sub>Entry</sub></th><th><sub>Optional</sub></th><th><sub>Default</sub></th><th><sub>Type</sub></th><th><sub>Description</sub></th><th><sub>Example</sub></th></tr>
<tr><td><sub>caldav:username</sub></td><td><sub>no</sub></td><td>-</td><td><sub>String</sub></td><td><sub>gives the username to login to the CalDAV server</sub></td><td><sub>foo</sub></td></tr>
<tr><td><sub>caldav:password</sub></td><td><sub>no</sub></td><td>-</td><td><sub>String</sub></td><td><sub>gives the password to the user for CalDAV server</sub></td><td><sub>bar</sub></td></tr>
<tr><td><sub>caldav:host</sub></td><td><sub>no</sub></td><td>-</td><td><sub>String</sub></td><td><sub>hostname or IP of the caldav server</sub></td><td><sub>caldavserver.intranet.local</sub></td></tr>
<tr><td><sub>caldav:tls</sub></td><td><sub>yes</sub></td><td><sub>true</sub></td><td><sub>boolean</sub></td><td><sub>disables or enables TLS/SSL usage (recommended not to disable)</sub></td><td><sub>true</sub></td></tr>
<tr><td><sub>caldav:strict-tls</sub></td><td><sub>yes</sub></td><td><sub>true</sub></td><td><sub>boolean</sub></td><td><sub>disables certifacate check, this might be used if certificates cannot be verified, this is a dangerous option as it voids a supposedly secure connection and gives free way to Man.In.Middle attacks, however - this optin might be used for debugging</sub></td><td><sub>false</sub></td></tr>
<tr><td><sub>caldav:port</sub></td><td><sub>yes</sub></td><td><sub>if tls =443 else =80</sub></td><td><sub>Int</sub></td><td><sub>Sets the port of the caldav HTTP(S) server to a non default. Attention - if enable TLS and set it to e.g. 80 (unsecure HTTP port) this might cause a error</sub></td><td><sub>8080</sub></td></tr>
<tr><td><sub>caldav:url</sub></td><td><sub>no</sub></td><td>-</td><td><sub>String</sub></td><td><sub>URL path to the CalDAV calendar collection which is used for home automation</sub></td><td><sub>/caldav.php/Heimauto/Planer/</sub></td></tr>
<tr><td><sub>caldav:refresh</sub></td><td><sub>yes</sub></td><td><sub>900</sub></td><td><sub>Int (SECONDS)</sub></td><td><sub>The refresh interval in SECONDS in which calendar entries are polled from server. The default should be OK - however this might be used to optimize load on CalDAv Server</sub></td><td><sub> 30</sub></td></tr>
</table>



## openhab.cfg Example
```
caldav:username=foo
caldav:password=supersecret
caldav:host=calendar.intranet.local
caldav:url=/caldav.php/Heimauto/Planer/
```


## Calendar Event Configuration

The event title can be anything and the event description will have the commands to execute.

The format of Calendar event description is simple and looks like this:

    start {
      send|update <item> <state>
    }
    end {
      send|update <item> <state>
    }

or just

    send|update <item> <state>

The commands in the `start` section will be executed at the event start time and the `end` section at the event end time. If these sections are not present, the commands will be executed at the event start time.

As a result, your lines in a Calendar event might look like this:

    start {
      send Light_Garden ON
      send Pump_Garden ON
    }
    end {
      send Light_Garden OFF
      send Pump_Garden OFF
    }

or just

    send Light_Garden ON
    send Pump_Garden ON


## Solving caldav IO errors:
To solve any issues with any binding, increase the logging. For caldav, add these lines to your 'logback.xml'

    <logger name="org.openhab.persistence.caldav" level="TRACE" />
    <logger name="org.openhab.io.caldav" level="TRACE" />

Please post in google openhab groups, if you are not able to resolve the problem.

