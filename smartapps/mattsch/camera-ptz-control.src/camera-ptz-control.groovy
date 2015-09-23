/**
 *  Camera PTZ Control
 *
 *  Copyright 2015 Matthew Schick
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
definition(
    name: "Camera PTZ Control",
    namespace: "mattsch",
    author: "Matthew Schick",
    description: "Point Foscam cameras places",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Choose Camera(s)") {
	input "cameras", "capability.imageCapture", multiple: false
	}

section("Choose Switch to control the PTZs") {
	input "switch1", "capability.switch", multiple: false, required: true
	}

section("Choose PTZs for on/off state of switch") {
	input(name: "ptz1", type: "enum", title: "Preset for on", options: ["1", "2", "3"])
	input(name: "ptz2", type: "enum", title: "Preset for off", options: ["1", "2", "3"])
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(switch1, "switch.on", switchHandler)
	subscribe(switch1, "switch.off", switchHandler)
}

def switchHandler(evt) {
	for(camera in cameras){
        if (evt.value == "off"){
            log.info "Camera has started panning"
            camera."preset${ptz2}"()
            log.info "Camera has set to preset${ptz2}"
        }
        else if (evt.value == "on"){
            camera."preset${ptz1}"()
            log.info "Camera has set to preset${ptz1}"
        }
    }
}