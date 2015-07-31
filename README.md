

# org.openhab.persistence.caldav
A CalDav persistence implementation for openHAB

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
- [Persistence] (#persistence)
- [Solving caldav persistence errors] (#solving-caldav-persistence-errors)


## Introduction
CalDav persistence binding implements the same functionality as the openHAB GCal binding but connects 
instead to Google Calendar to any CalDAV enabled calendar server. At the moment it is used in a very similar 
way as the GCal binding.

CalDav persistence allows you to store events from openHAB in a CalDAV calendar and use those events as a presence simulation in
a home automation environment.

## Thanks
--> to all who have contributed to [GCal](https://github.com/openhab/openhab/wiki/GCal-Binding) Persistence, as this plugin is following GCal implementation and configuration

## Notice
- CalDAV Persistence binding is beta and just tested on openHAB 1.8, on a few systems and needs more review
- CalDAV Persistence binding is not fully integrated into openHAB's build system, for org.openhab.persistence.caldav the maven build is disfunctional. You are wellcome to contribute here ...
- CalDAV persistence is part of org.openhab.io.caldav - which needs to be installed as well !

## Compatibility
- openHAB 1.8 (tested)
- java 1.7
- followigng calendar Servers have been tested:
  - [radicale](http://radicale.org) (tested, doesn't implement full requied function set, however it might work)
  - [davical](http://www.davical.org) (tested, fully compatible)

Please contact over openHAB Google group if you like to share your testing expirence.

## Preconditions
- You have installed a working calendar server such as
  - [davical](http://www.davical.org) (tested)
  - [bedework](https://www.apereo.org/projects/bedework) (un-tested)
  - or Apple's [Calendar Server](http://calendarserver.org) (un-tested)
- You are able to access this calender over http(s) via any kind of CalDAV application
- in case of using certificates (recommended) you have server certificate and eventually CA certificate at hand
- You have downloaded and configured org.openhab.io.caldav binding
- You have downloaded and configured org.openhab.persistence.caldav binding
- run openhab eventually in debug mode to see the persistence service working
- the user you'll configure to caldav-persistence will need read/write rights to the CalDAV collection/calendar on CalDAV server

## Install
At the moment there are precompiled exports from eclipse available [download org.openhab.persistence.caldav_XXXX.jar] (https://raw.githubusercontent.com/tseiman/org.openhab.persistence.caldav/master/build/org.openhab.persistence.caldav_1.8.0.201507300342.jar) and place it in the `OPENHAB_ROOT/addons` folder.

## Configure
Following configuration entries are supported for openhab.cfg:

<table>
<tr><th><sub>Entry</sub></th><th><sub>Optional</sub></th><th><sub>Default</sub></th><th><sub>Type</sub></th><th><sub>Description</sub></th><th><sub>Example</sub></th></tr>
<tr><td><sub>caldav-persistence:username</sub></td><td><sub>no</sub></td><td>-</td><td><sub>String</sub></td><td><sub>gives the username to login to the CalDAV server</sub></td><td><sub>foo</sub></td></tr>
<tr><td><sub>caldav-persistence:password</sub></td><td><sub>no</sub></td><td>-</td><td><sub>String</sub></td><td><sub>gives the password to the user for CalDAV server</sub></td><td><sub>bar</sub></td></tr>
<tr><td><sub>caldav-persistence:host</sub></td><td><sub>no</sub></td><td>-</td><td><sub>String</sub></td><td><sub>hostname or IP of the caldav server</sub></td><td><sub>caldavserver.intranet.local</sub></td></tr>
<tr><td><sub>caldav-persistence:tls</sub></td><td><sub>yes</sub></td><td><sub>true</sub></td><td><sub>boolean</sub></td><td><sub>disables or enables TLS/SSL usage (recommended not to disable)</sub></td><td><sub>true</sub></td></tr>
<tr><td><sub>caldav-persistence:strict-tls</sub></td><td><sub>yes</sub></td><td><sub>true</sub></td><td><sub>boolean</sub></td><td><sub>disables certifacate check, this might be used if certificates cannot be verified, this is a dangerous option as it voids a supposedly secure connection and gives free way to Man.In.Middle attacks, however - this optin might be used for debugging</sub></td><td><sub>false</sub></td></tr>
<tr><td><sub>caldav-persistence:port</sub></td><td><sub>yes</sub></td><td><sub>if tls =443 else =80</sub></td><td><sub>Int</sub></td><td><sub>Sets the port of the caldav HTTP(S) server to a non default. Attention - if enable TLS and set it to e.g. 80 (unsecure HTTP port) this might cause a error</sub></td><td><sub>8080</sub></td></tr>
<tr><td><sub>caldav-persistence:url</sub></td><td><sub>no</sub></td><td>-</td><td><sub>String</sub></td><td><sub>URL path to the CalDAV calendar collection which is used for home automation</sub></td><td><sub>/caldav.php/Heimauto/Planer/</sub></td></tr>
<tr><td><sub>caldav-persistence:offset</sub></td><td><sub>yes</sub></td><td><sub>14</sub></td><td><sub>Int (DAYs)</sub></td><td><sub>The offset in DAYs, in advance where the persistence service will store the actual events captured from openHAB</sub></td><td><sub>12</sub></td></tr>
<tr><td><sub>caldav-persistence:upload-interval</sub></td><td><sub>yes</sub></td><td><sub>10</sub></td><td><sub>Int (SECONDS)</sub></td><td><sub>The upload interval in SECONDS default should be OK - however this might be used to optimize load on CalDAv Server</sub></td><td><sub> 30</sub></td></tr>
<tr><td><sub>caldav-persistence:executescript</sub></td><td><sub>yes</sub></td><td><sub>&gt; if (PresenceSimulation.state == ON) %s.sendCommand(%s)</sub></td><td><sub>String</sub></td><td><sub>changes the script for persistence execution, especally the name of the Switch item which is per default `PresenceSimulation` can be changed</sub></td><td><sub>&gt; if (SomeSwitchItemName.state == ON) %s.sendCommand(%s)</sub></td></tr>
</table>



## openhab.cfg Example
```
caldav-persistence:username=foo
caldav-persistence:password=supersecret
caldav-persistence:host=calendar.intranet.local
caldav-persistence:url=/caldav.php/Heimauto/Planer/
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

## Persistence
The CalDAV persistence bundle can be used to realize a simple but effective Presence Simulation feature (thanks to GCal contributors, providing the concept). Every single change of an item that belongs to a certain group is posted as new calendar entry in the future. By default each entry is posted with an offset of 14 days (If you'd like to change the offset please change the parameter `caldav-persistence:offset` in your `openhab.cfg`). Each calendar entry looks like the following:

- title: `[PresenceSimulation] <itemname>`
- content: `> if (PresenceSimulation.state == ON) sendCommand(<itemname>,<value>)`

- Make sure that the binding org.openhab.persistence.caldav is installed and configured (see above)
- make sure your items file contains items that belong to a group which is configured in `.persist` file. In this example (following GCal configuration) it is  `PresenceSimulationGroup` - if you would like to change the group name change it at `caldav-persistence.persist`.
- make sure your items file contains an (Switch) item called `PresenceSimulation` which is referred by the scripts executed at a certain point in time - if you would like to change the group name please change the parameter `caldav-persistence:executescript` in your `openhab.cfg`.
- make sure the referenced CalDAV calendar is writeable by the given user

Note: you also need to configure the caldav-io binding (CalDAV Calendar Configuration in 'openhab.cfg') to be able to read the entries from the calendar and act on it!

To activate the Presence Simulation simply set `PresenceSimulation` to `ON` and the already downloaded events are being executed. Your smartHome behaves like you did 14 days ago.

A sample `caldav-persistence.persist` file looks like this:

    Strategies {
    	default = everyChange
    }
    
    Items {
    	PresenceSimulationGroup* : strategy = everyChange
    }

## Solving caldav persistence errors:
To solve any issues with any binding, increase the logging. For caldav, add these lines to your 'logback.xml'

    <logger name="org.openhab.persistence.caldav" level="TRACE" />
    <logger name="org.openhab.io.caldav" level="TRACE" />

Please post in google openhab groups, if you are not able to resolve the problem.

