/**
 *  Monoprice Z-Wave Smoke Detector
 *
 *  Copyright 2015 Jim Worley
 *		Updated by Cuboy29: 8/24 - Add ability to change wakeup time.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

preferences {
    // manufacturer default wake up is every hour; optionally increase for better battery life
    input "userWakeUpInterval", "number", title: "Wake Up Interval (seconds)", description: "Default 3600 sec (10 minutes - 7 days)", defaultValue: '3600', required: false, displayDuringSetup: true
}

metadata {
	definition (name: "Monoprice Z-Wave Smoke Detector", namespace: "Smartthings", author: "Jim Wolley") {
		capability "Smoke Detector"
		capability "Sensor"
		capability "Battery"

		attribute "alarmState", "string"
		attribute "deviceAlert", "string"
        
        //Support Command Class
        //0x30 Sensor Binary
        //0x71 Alarm/Notification
        //0x72 Manufacture Specific
        //0x86 Version
        //0x85 Association V2
        //0x80 Battery
        //0x84 Wake Up V2

		fingerprint deviceId: "0xA100", inClusters: "0x30,0x71,0x72,0x86,0x85,0x80,0x84"
	}

	simulator {	
		status "smoke/test": "command: 7105, payload: 01 FF 00 FF 01 02 00 00"
        status "clear": "command: 7105, payload: 01 00 00 FF 01 02 00 00"
        status "tamper" : "command: 7105, payload: 01 FF 00 FF 01 FE 00 00"
        status "wakeUp Device" : "command: 8407, payload:"
		status "battery 100%": "command: 8003, payload: 64"
		status "battery 5%": "command: 8003, payload: 05"
	}

	tiles {
		standardTile("smoke", "device.alarmState", width: 2, height: 2) {
			state("clear", label:"clear", icon:"st.alarm.smoke.clear", backgroundColor:"#ffffff")
			state("smoke", label:"SMOKE", icon:"st.alarm.smoke.smoke", backgroundColor:"#e86d13")
            state("tamper", label:"tamper", icon:"st.alarm.smoke.smoke", backgroundColor:"#e86d13")
			state("lowbat", label:"lowbat", icon:"st.alarm.smoke.smoke", backgroundColor:"#e86d13")
		}
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") {
			state "battery", label:'${currentValue}% battery', unit:""
		}

        
		main "smoke"
		details(["smoke", "battery"])
	}
}

// parse events into attributes
def parse(String description) {
    log.debug "Smoke Detector description: $description"
	def results = []
	if (description.startsWith("Err")) {
	    results << createEvent(descriptionText:description, displayed:true)
	} else {
		def cmd = zwave.parse(description, [0x71: 2, 0x84: 2, 0x80: 1])
		if (cmd) {
            //log.debug "Smoke Detector CMD: $cmd"
            //log.debug "Smoke Detector CMD properties:  ${cmd.getProperties()}"
			zwaveEvent(cmd, results)
		}
	}
	log.debug "\"$description\" parsed to ${results.inspect()}"
	return results
}

def createSmokeEvents(name, results) {

    if (name == "smoke") {
		// these are displayed:false because the composite event is the one we want to see in the app
		results << createEvent(name: "smoke", value: "detected", descriptionText: "$device.displayName smoke was detected!", displayed: false)
        // This composite event is used for updating the tile
		results << createEvent(name: "alarmState", value: name, descriptionText: text)
	} else if (name == "clear") {
		results << createEvent(name: "smoke", value: "clear", descriptionText: "$device.displayName smoke is clear!", displayed: false)
        results << createEvent(name: "alarmState", value: name, descriptionText: text)
	} else if (name == "tamper"){
    	results << createEvent(name: "smoke", value: "tamper", descriptionText: "$device.displayName has been tampered with!", displayed: false)
        results << createEvent(name: "alarmState", value: name, descriptionText: text)
		results << createEvent(name: "deviceAlert",value: name, descriptionText:"$device.displayName has been tampered with!")
	} else if (name == "lowbat"){
   		results << createEvent(name: "smoke", value: "lowbat", descriptionText: "$device.displayName has been tampered with!", displayed: false)
    	results << createEvent(name: "alarmState", value: name, descriptionText: text)
        results << createEvent(name: "deviceAlert",value: name,descriptionText:"$device.displayName has a low battery!")
	}
}

def zwaveEvent(physicalgraph.zwave.commands.alarmv2.AlarmReport cmd, results) {
	
    if (cmd.zwaveAlarmEvent == 2){
		createSmokeEvents(cmd.alarmLevel ? "smoke" : "clear", results)
    } else if (cmd.zwaveAlarmEvent == 254){
		createSmokeEvents("tamper", results)
    } else {
    	createSmokeEvents("lowbat", results)
    }
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd, results)
{
        createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)
        def userWake = getUserWakeUp(userWakeUpInterval)
        
        // Only ask for battery if we haven't had a BatteryReport in a while
        if (!state.lastbatt || (new Date().time) - state.lastbatt > 50*60*1000) {
                results << response(zwave.batteryV1.batteryGet())
                results << response("delay 1500")  // leave time for device to respond to batteryGet
        }
        // If user has changed userWakeUpInterval, send the new interval to the device 
    	if (state.wakeUpInterval != userWake){
       		state.wakeUpInterval = userWake
            log.debug "Setting New WakeUp Interval to: " + state.wakeUpInterval
        	results << response(zwave.wakeUpV2.wakeUpIntervalSet(seconds:state.wakeUpInterval, nodeid:zwaveHubNodeId))
        	results << response("delay 200")
        	results << response(zwave.wakeUpV2.wakeUpIntervalGet())
            results << response("delay 1500")
    	}  
        results << new physicalgraph.device.HubAction(zwave.wakeUpV2.wakeUpNoMoreInformation().format())
		results << createEvent(descriptionText: "$device.displayName woke up", isStateChange: false)
		results << createEvent(name: "deviceAlert",value:"clear",descriptionText:"No more alerts are found!")
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd, results) {
	def map = [ name: "battery", unit: "%" ]
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "$device.displayName battery is low!"
	} else {
		map.value = cmd.batteryLevel
        results << createEvent(name: "deviceAlert",value:"clear",descriptionText:"Reported Battery Level: $cmd.batteryLevel")
	}
	results << createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpIntervalCapabilitiesReport cmd, results) {

    def map = [ name: "defaultWakeUpInterval", unit: "seconds" ]
	map.value = cmd.defaultWakeUpIntervalSeconds
	map.displayed = false
	state.defaultWakeUpInterval = cmd.defaultWakeUpIntervalSeconds
    results << createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpIntervalReport cmd, results) {

	def map = [ name: "reportedWakeUpInterval", unit: "seconds" ]
	map.value = cmd.seconds
	map.displayed = false
    results << createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.Command cmd, results) {
	def event = [ displayed: false ]
	event.linkText = device.label ?: device.name
	event.descriptionText = "$event.linkText: $cmd"
	results << createEvent(event)
}


private getUserWakeUp(userWake) {

    if (!userWake)                       { userWake =     '3600' }  // set default 1 hr if no user preference 
    // make sure user setting is within valid range for device 
    if (userWake.toInteger() <       60) { userWake =       '600' }  // 10 minutes - Mininum
    if (userWake.toInteger() > 604800) { userWake = '604800' }  // 1 week - Maximum
    return userWake.toInteger()
}