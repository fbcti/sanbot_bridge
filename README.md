# Sanbot Bridge Application
SanbotBridge is an Android application that turns a Sanbot robot into a network-accessible automation endpoint. Its main purpose is to expose the robot's local capabilities through a stable bridge layer so that external clients can control motion, speech, lighting, sensors, camera features, audio, and selected screen behavior over REST and WebSocket APIs. In practice, the application acts as the robot-side runtime that accepts remote requests, translates them into Sanbot SDK or Android operations, and returns structured results to the caller.

The application is designed to run directly on the robot, but it also includes an emulator-oriented flavor for development in Android Studio. That makes it possible to work on the transport layer, request routing, and configuration behavior without always needing physical robot hardware. The two product flavors share most of the codebase, while the robot flavor binds to the Sanbot SDK and the emulator flavor substitutes that runtime with simulated behavior.

# Purpose and Functionality

SanbotBridge exists to separate remote-control clients from the details of the Sanbot OpenSDK. Instead of requiring every client to understand robot-specific classes and Android service behavior, the application provides a consistent network API. The bridge exposes a primary WebSocket command channel at `/ws` and also serves HTTP endpoints for diagnostics, command routing, and binary media retrieval.

Through that API surface, clients can perform a broad range of operations. They can move the robot base, position the head, drive the arms, control LEDs and the head light, trigger face expressions, configure sensor forwarding, adjust camera behavior, capture still images or snapshots, play or record audio, and invoke text-to-speech. The bridge also publishes events back to connected clients, which allows sensor changes, alarms, and other asynchronous signals to be pushed instead of polled.

The app includes a built-in Android user interface for local operation. The main screen starts the bridge service, displays its current status, and shows a rolling traffic log of recent requests and responses. A separate settings screen lets an operator manage persisted values such as the robot name, HTTP port, API key, selected TTS engine, and feature toggles like speech recognition, face detection, and home alarm detection.

# Building and Running

SanbotBridge is an Android Studio project with a single application module, `:app`. The application package is `com.fbcti.sanbot.bridge`; the emulator flavor adds the `.emulator` suffix so both variants can coexist on the same device. The current application version in `app/build.gradle` is `1.0.001`, represented as `versionCode 1001` and `versionName "1.0.001"`.

## Project configuration

The project uses the included Gradle wrapper and requires an Android SDK configured through `local.properties`. It uses Gradle 7.2 with Android Gradle Plugin 7.1.3. The module is configured with `compileSdkVersion 28`, `minSdkVersion 21`, `targetSdkVersion 23`, and Java 8 source and target compatibility to match the older robot environment.

The manifest requests storage, camera, and Wi-Fi-state permissions, which allow the bridge to persist media, use camera devices, and report connectivity information. The module depends on Android support libraries, Gson, and separately downloaded robot and camera SDK libraries under `app/libs`.

## Vendor SDK dependencies

The proprietary vendor SDK binaries are not included in this repository. Before building the project, create the `app/libs` directory if it does not exist and obtain the following archives from their respective vendors:

- `SanbotOpenSDK_2.0.1.10.aar`: obtain Sanbot OpenSDK version 2.0.1.10 from the [Sanbot/Qihan developer portal](http://www.qihancloud.com/dev/docs/robot.html?lang=en-us), your Sanbot distributor, or Sanbot support.
- `OrbbecOpenNISDK_2.3.0.85.aar`: obtain OpenNI SDK version 2.3.0.85 for Android from the [official Orbbec OpenNI SDK releases](https://github.com/orbbec/OpenNI_SDK/releases) or Orbbec support.

Place both files in `app/libs` without changing their filenames:

```text
app/
  libs/
    SanbotOpenSDK_2.0.1.10.aar
    OrbbecOpenNISDK_2.3.0.85.aar
    openni2.3.jar
```

The build also uses `openni2.3.jar` as a compile-time dependency. An AAR is a ZIP-compatible archive; extract `libs/openni2.3.jar` from `OrbbecOpenNISDK_2.3.0.85.aar` and copy it to `app/libs/openni2.3.jar` as shown above.

Retain and comply with all license and notice files supplied by the vendors. Do not publish or redistribute these binaries unless the applicable vendor license or written vendor permission expressly allows it. The filenames are referenced directly by `app/build.gradle`, so using another SDK version requires updating the corresponding dependency declarations and may require source changes.

## Build variants

Two product flavors are defined under the `runtime` dimension:

- The `robot` flavor sets `EMULATOR_MODE` to `false` and binds to the bundled Sanbot SDK AAR.
- The `emulator` flavor sets `EMULATOR_MODE` to `true`, changes the application id, and appends `-emulator` to the version name.

Release builds currently reuse the debug signing configuration and do not enable code shrinking.

## Build and install

Build the robot or emulator debug variant from the repository root:

```powershell
.\gradlew.bat --no-daemon app:assembleRobotDebug
.\gradlew.bat --no-daemon app:assembleEmulatorDebug
```

The build includes output naming logic that writes generated APKs below `app/build/outputs/apk/<flavor>/debug/` with human-readable names. The robot build is named `Sanbot Bridge-1.0.001-debug.apk`; the emulator build is named `Sanbot Bridge-1.0.001-emulatorDebug.apk`.

To install and start the robot build on an ADB-connected device, run:

```powershell
.\install-and-start.bat [device-address]
```

If no address is supplied, the script uses `10.30.12.111:5555`. It installs the APK with `adb install -r` and starts `BridgeMainActivity`.

## Runtime configuration and files

At runtime, the application uses a private `config.json` file managed by `BridgeConfig`. Important defaults include port `8088`, API key `sanbot-bridge`, a default robot name of `Sanbot`, Android or bridge-selected speech settings, and structured per-camera, per-sensor, and per-TTS parameter groups. The settings model also stores feature flags for ASR, face detection, and home alarm detection, which affect what the running bridge enables or forwards.

User-accessible media and scripts are stored under the external-storage `SanbotBridge` directory:

- `SanbotBridge/audio` contains saved WAV recordings and local playback files.
- `SanbotBridge/images` contains images available to `command:screen:image`.
- `SanbotBridge/scripts` contains uploaded UTF-8 `.scr` files.

File paths supplied through the API are relative to their respective directory. Audio and script paths are canonicalized and checked to prevent escaping their designated directories.

## Included demo clients

The `client` directory contains four browser-based demo clients:

- `websocket-console.html` connects to `/ws`, sends request templates, and displays responses and events.
- `rest-console.html` provides a Swagger-based explorer for the REST API.
- `camera-console.html` displays camera streams and snapshots.
- `dashboard-console.html` provides a configurable command dashboard.

For more information, see the [Demo Clients](#demo-clients) section below.

# Architecture

The runtime is centered on `BridgeService`, which is the long-lived background service that owns the bridge lifecycle. It loads configuration, binds to the Sanbot SDK in robot mode, activates emulator behavior in emulator mode, creates the HTTP server, manages REST and WebSocket transports, keeps in-memory traffic logs, and coordinates access to the robot-facing units. It is the operational heart of the application.

`BridgeMainActivity` is the local entry point and operator console. It starts the service when the required permissions are available, keeps the bridge UI visible when appropriate, refreshes status and log output, and provides a host for speech and screen-overlay behaviors that need activity context. The activity can also be started in a hidden mode so the bridge service can come up without showing the normal UI.

`BridgeSettingsActivity` provides the configuration UI. It reads the persisted configuration model, lets the operator edit supported settings, validates user input, and saves the result back to disk. Those settings are stored through `BridgeConfig`, which is responsible for default values, serialization, deserialization, normalization, and structured storage for camera, sensor, and speech parameter blocks.

Request dispatch is handled by `BridgeRequestHandler`. This class is protocol-neutral at the business-logic level: it receives normalized bridge requests and routes them by request type, module, and action. It validates required parameters, separates fixed parameters from flexible ones, and forwards the operation to either the service itself or a more specialized unit.

Below that routing layer, the application is organized into bridge units and support managers. The bridge units wrap either Sanbot SDK functionality or Android-native functionality behind a more uniform application-facing interface. Examples include motion, LED, face, sensor, camera, audio, ASR, and TTS units. Camera-specific manager classes handle Android, Sanbot HD, and Orbbec camera behavior, while transport classes encapsulate HTTP request parsing, WebSocket session handling, JSON responses, media responses, and event broadcasting.

This layered structure gives the app a clear separation of concerns. Activities own local UI concerns, the service owns lifecycle and connectivity concerns, the request handler owns protocol routing and validation, and the units own device-specific behavior. That separation makes the project easier to extend when new modules, commands, or hardware-backed capabilities need to be added.

# Operational Notes

The bridge listens on HTTP port `8088` by default and exposes its primary WebSocket control endpoint at `ws://<robot-ip>:8088/ws`. REST helpers such as `/status` and `/openapi` are available for diagnostics, while command and media routes provide the remote-control surface used by clients. REST `GET` info requests are public except for `info:bridge:config`, which requires the configured API key because its response contains sensitive settings. Other REST requests also require the configured API key. WebSocket sessions must be explicitly authorized, and only the active read-write session may issue commands.

The local UI retains the latest 50 traffic-log entries. Each displayed message is normalized to one line and truncated to 140 characters before the timestamp and optional ellipsis are added. The complete untruncated message is still written to the Android debug log under the `BridgeTraffic` tag.

From a maintenance perspective, the most important thing to understand is that SanbotBridge is not just a UI app. It is a service-oriented integration layer between Android, the Sanbot robot SDK, optional emulator behavior, and remote control clients. Most changes should therefore be evaluated in terms of request routing, unit behavior, configuration persistence, and compatibility across both robot and emulator modes.

# REST and WebSocket Protocol

This document describes the REST and WebSocket request API implemented by `BridgeRequestHandler`.

## Overview

- REST base URL: `http://<robot-ip>:8088`
- WebSocket endpoint: `ws://<robot-ip>:8088/ws`
- Info routes: `GET /v1/info/<module>/<action>`
- Query routes: `POST /v1/query/<module>/<action>`
- Command routes: `POST /v1/command/<module>/<action>`
- Media routes: `GET /v1/media/<module>/<action>`
- WebSocket requests contain `id`, `type`, `module`, `action`, and optional `data`.
- Supported request types are `info`, `query`, `command`, and `media`.
- REST `GET` info requests are public except for `info:bridge:config`. That request and all other REST requests require the configured API key in the `X-API-Key` header or `api_key` query parameter.
- REST `POST` requests accept a direct data object or an envelope containing an optional `id` and nested `data` object.
- REST media endpoints return binary data. Successful WebSocket media requests return a JSON metadata frame followed by one binary frame. Continuous audio and video streaming is not supported over WebSocket.

## WebSocket session flow

1. Connect to the WebSocket endpoint.
2. Send `bridge:authorize` as a `type: "command"` request.
3. The first authorized active socket receives `read_write`; later authorized sockets receive `read_only`.
4. Send commands only from the read-write socket.
5. Info, query, and media requests may be sent from authorized sockets as permitted by the service.

## Request shapes

```json
{
  "id": "req-1",
  "type": "command",
  "module": "speech",
  "action": "say",
  "data": {
    "text": "Hello"
  }
}
```

```json
{
  "id": "req-2",
  "type": "media",
  "module": "camera",
  "action": "snapshot",
  "data": {
    "camera": "orbbec-color"
  }
}
```

## Response shape

```json
{
  "id": "req-1",
  "type": "response",
  "module": "speech",
  "action": "say",
  "code": 200,
  "message": "robot request succeeded",
  "data": {
    "errorCode": "OK",
    "description": "speech started",
    "result": true
  }
}
```

Response codes use HTTP-like values: `200` for success, `400` for malformed input, `401` for missing or invalid authentication, `403` for insufficient WebSocket authorization, `404` for an unsupported route, `406` for invalid command data, `500` for internal failures, and `503` when requested data or a service is temporarily unavailable.

## Info requests

Info requests are synchronous; the result object contains the actual response data.

### info:bridge:openapi
Requests the robot to return a list of available OpenAPI endpoints. The payload for this request is empty.

### info:bridge:status
Requests the robot to return the status for one or more bridge units. The payload for this request is
```json
{
  "unit" (optional): bridge unit name or array of bridge unit names
}
```
Supported values of `unit` are:
  - `bridge`
  - `system`
  - `motion`
  - `led`
  - `face`
  - `sensor`
  - `camera`
  - `audio`
  - `tts`
  - `asr`

If one or more units are specified, only status information for those units is returned. If no unit is specified, status information for all units is returned. Unit names are trimmed and converted to lowercase. Invalid entries are ignored.

#### Example
To retrieve the status of the `motion` and `led` units add the following request data to the payload
```json
{
  "unit": [ "motion", "led" ]
}
```
The response data for this request is
```json
{
  "errorCode": "SUCCESS",
  "description": "operation_success",
  "result": {
    "motionUnit": {
      "status": "started",
      "wheels": "idle",
      "charging": false,
      "wandering": false,
      "following": false,
      "duckrunning": false
    },
    "ledUnit": {
      "status": "started"
    }
  }
}
```

### info:bridge:config
Requests the robot to return the bridge configuration data. The payload for this request is empty.

Unlike other REST info requests, this request requires the configured API key because its response contains sensitive settings. Authorized WebSocket sessions may request it normally.

#### Example
An example of the response data for this request is
```json
{
  "errorCode": "SUCCESS",
  "description": "operation_success",
  "result": {
    "apiKey": "sanbot-bridge",
    "cameraName": "sanbot",
    "cameras": {
      "cameras": {
        "android": {
          "cameraName": "android",
          "cameraParams": {},
          "sensorParams": {}
        },
        "orbbec": {
          "cameraName": "orbbec",
          "cameraParams": {},
          "sensorParams": {
            "color": {},
            "depth": {},
            "ir": {}
          }
        },
        "sanbot": {
          "cameraName": "sanbot",
          "cameraParams": {},
          "sensorParams": {}
        }
      }
    },
    "enableAlarmDetection": true,
    "enableAsr": false,
    "enableFaceDetection": true,
    "httpPort": 8088,
    "robotName": "Sanbot",
    "sensorParams": {
      "params": {
        "touch": {
          "enable": false
        },
        "obstacle": {
          "enable": false
        },
        "pir": {
          "enable": false
        },
        "infrared": {
          "enable": false
        },
        "voicelocate": {
          "enable": false
        },
        "orientation": {
          "enable": false
        },
        "pocsensors": {
          "enable": false
        }
      }
    },
    "ttsName": "android",
    "ttsParams": {
      "params": {
        "android": {
          "engine": "",
          "language": "en-US",
          "voice": "",
          "speed": 100,
          "pitch": 100
        },
        "sanbot": {
          "engine": "",
          "language": "en-US",
          "voice": "",
          "speed": 100,
          "pitch": 100
        }
      }
    }
  }
}
```

### info:sensor:orientation
Requests the robot to return cached orientation data. The payload is empty. The returned data contains the yaw, pitch, and roll that describe the robot's orientation in three-dimensional space.

Example
An example of the response data for this request is
```json
{
  "errorCode": "SUCCESS",
  "description": "operation_success",
  "result": {
    "timestamp": 1789971584,
    "yaw": 85,
    "pitch": 0.1,
    "roll": 0,
    "sensitivity": 90
  }
}
```

### info:sensor:infrared
Requests the robot to return aggregated infrared sensor data. The payload is empty. For each of the seventeen infrared sensors that fired during the configured reporting interval, the returned data contains the sample count, last value, average, minimum, maximum, strength, and timestamps.

#### Example
An example of the response data for this request is
```json
{
  "errorCode": "SUCCESS",
  "description": "operation_success",
  "result": {
    "timestamp": 1789977621,
    "sensorsFired": "3,4",
    "sensorData": [
      {
        "sensor": 3,
        "count": 5,
        "last": 25,
        "average": 25,
        "min": 25,
        "max": 25,
        "strength": 0.65,
        "firstTimestamp": 1789977618,
        "lastTimestamp": 1789977619
      },
      {
        "sensor": 4,
        "count": 5,
        "last": 29,
        "average": 28.6,
        "min": 28,
        "max": 29,
        "strength": 0.6,
        "firstTimestamp": 1789977618,
        "lastTimestamp": 1789977619
      }
    ],
    "sensitivity": 90,
    "updateInterval": 1000,
    "strongest": {
      "sensorId": 3,
      "strength": 0.65
    },
    "direction": {
      "x": -0.8539999999999999,
      "y": 0.05,
      "label": "lower_left"
    }
  }
}
```
### info:camera:features
Requests the robot to return a list of features supported by the specified camera. The payload for this request is
```json
{
  "camera": (optional) camera name, one of ["sanbot", "head", "android", "body", "orbbec", "3d"]
}
```
The `camera` property is optional. If it is omitted, features for the currently selected camera are returned. The `head`, `body`, and `3d` values are aliases for the `sanbot`, `android`, and `orbbec` cameras.

#### Example
To retrieve the features of the `sanbot` camera, add the following request data to the payload:
```json
{
  "camera":  "sanbot"
}
```
The response data for this request is
```json
{
  "errorCode": "SUCCESS",
  "description": "operation_success",
  "result": {
    "cameraName": "sanbot",
    "cameraAlias": "head",
    "liveCaptureModes": [
      "yuv (640x480, YUV encoding)",
      "rgb (640x480, RGB encoding)"
    ],
    "stillCaptureModes": [
      "yuv (1280x720, YUV encoding)",
      "rgb (1280x720, RGB encoding)"
    ]
  }
}
```

### info:audio:volume
Requests the robot to return the volume for one or more Android audio streams. The payload is:
```json
{
  "stream": (optional) stream for which to retrieve volume
}
```
Supported streams are `music`, `tts`, `system`, `alarm`, `voice_call`, `ring`, and `notification`. If no stream is specified, the volume for the `music`, `tts`, and `system` streams is returned. Volumes are expressed as percentages of their respective maximum values.

#### Example
To retrieve the volume of the `music` stream add the following request data to the payload
```json
{
  "stream": "music"
}
```
The response data for this request is
```
{
  "errorCode": "SUCCESS",
  "description": "operation_success",
  "result": {
    "musicVolume": 58
  }
}
```

### info:speech:features
Requests the robot to return a list of features supported by the active speech platform(s). The payload for this request is empty. The returned data contains both the features provided by either the Sanbot or native Android text-to-speech platform, and the features provided by the Sanbot speech recognition platform.

#### Example
An example of the response data for this request is
```json
{
  "errorCode": "SUCCESS",
  "description": "operation_success",
  "result": {
    "tts": {
      "platform": "Android",
      "languages": [
        "en-GB",
        "en-US",
        "nl-NL",
        "de-DE",
        ...
      ],
      "engines": [
        {
          "defaultLanguage": "en_US",
          "voices": {
            "en_GB-locale": {
              "locale": "en_GB",
              "quality": 500,
              "latency": 400,
              "networkConnectionRequired": true
            },
            "nl_NL-locale": {
              "locale": "nl_NL",
              "quality": 500,
              "latency": 400,
              "networkConnectionRequired": true
            },
            ...
          }
        }
      ],
      "defaultEngine": "com.google.android.tts"
    },
    "asr": {
      "platform": "Sanbot",
      "enabled": false
    }
  }
}
```


### info:speech:status
Requests the robot to return the status of the speech platforms. The payload is empty.

This request is an alias for `info:bridge:status` with both the text-to-speech and speech-recognition units selected.

### info:battery:status
Requests the robot to return the battery status. The payload is empty.

#### Example
An example of the response data for this request is
```json
{
  "errorCode": "SUCCESS",
  "description": "operation_success",
  "result": {
    "batteryStatus": {
      "value": 2,
      "status": "charge_pile"
    },
    "batteryLevel": 86
  }
}
```
### info:script:list

Requests the list of available scripts. The payload is empty. Script names are returned without the `.scr` extension and in alphabetical order.

#### Example
An example of the response data for this request is
```json
{
  "errorCode": "SUCCESS",
  "description": "operation_success",
  "result": {
    "scripts": [
      "forward_backward",
      "squarewalk"
    ]
  }
}
```
## Query requests

Query requests are asynchronous, the result object only specifies if the query was successfully submitted, not the actual response data.


### query:led:whitelight
Requests the robot to query the status of the white light in the robot head. The payload for this request is
```json
{
  "refid": (optional) reference id for the asynchronous query result
}
```
If `refid` is not specified the request id will be used as reference id.

An `event:led:whitelight` event published as a result of this request contains the white-light status.

## Command requests
Command requests instruct the robot to move, control hardware, speak, listen, or perform another action.

### command:robot:move
Instructs the robot to move in the specified direction. The payload for this request is:
```json
{
  "direction": (mandatory) direction in which to move,
  "duration": (optional) duration in units of 100ms, default 0,
  "speed": (optional) speed in range [1..10], default 5,
  "nowait": (optional) one of [true, false]; see below
}
```
Supported values of `direction` are
  - `forward` - moves the robot forward
  - `backward` - move the robot backward
  - `left` - move the robot left (without turning)
  - `right` - move the robot right (without turning)
  - `left_forward` - turn 45 degrees left and move forward
  - `right_forward` - turn 45 degrees right and move forward
  - `left_backward` - turn 135 degrees left and move backward
  - `right_backward` - turn 135 degrees right and move backward
  - `left_turn` - turn left (counter-clockwise).
  - `right_turn` - turn right (clockwise)
  - `left_circle` - circle left (counter-clockwise)
  - `right_circle` - circle right (clockwise)
  - `stop_turn` - stop turning
  - `stop` - stop all movement
  - `reset` - reset wheel motion

If `duration` is equal to 0 motion continues until explicitly stopped by a stop request. By default, 
the command is only executed if the previous requested motion operation has completed. If @c nowait 
equal @c true, the command is executed immediately, interrupting the current operation.

### command:robot:walk
Instructs the robot to move the specified distance forward or to stop moving forward. The payload for this request is
```json
{
  "distance": (mandatory) distance in centimeters,
  "speed": (optional) speed in range [1..10], default 5,
  "nowait": (optional) one of [true, false]; see below
}
```
By default, the command is only executed if the previous requested motion operation has completed. 
If @c nowait equal @c true, the command is executed immediately, interrupting the current operation.

### command:robot:turn
Instructs the robot to turn left or right by the specified angle. The payload for this request is
```json
{
  "direction": (mandatory) one of [`left`, `right`, `stop`],
  "angle": (mandatory) angle in degrees,
  "speed": (optional) speed in range [1..10], default 5,
  "nowait": (optional) one of [true, false]; see below
}
```
By default, the command is only executed if the previous requested motion operation has completed. 
If @c nowait equal @c true, the command is executed immediately, interrupting the current operation.

### command:robot:modular
Instructs the robot to start or stop one of the modular motion modes. The payload for this request is:
```json

{
  "mode": (mandatory) one of ["wander", "follow", "duckrun"],
  "action": (mandatory) one of ["start", "stop"],
  "info": (optional) additional modular mode info
}
```
The role of the `info` property is unknown.

### command:robot:stop
Instructs the robot to stop moving. The payload for this request is empty.

### command:robot:reset
Requests the robot to reset. The payload for this request is empty.

### command:robot:test
Instructs the robot to execute a test function (for development and testing only). The payload for this request is:
```json
{
  "func": (mandatory) bridge service test method name,
  "param1": (optional) first string parameter,
  ...
  "param9": (optional) ninth string parameter
}
```

### command:head:move
Instructs the robot to move the head in the specified direction. The payload for this request is:
```json
{
  "direction": (mandatory) direction in which to move,
  "angle": (mandatory for absolute movement, optional for relative movement) absolute or relative angle
}
```
Supported values of `direction` are
  - `horizontal`
  - `vertical`
  - `left`
  - `right`
  - `up`
  - `down`
  - `left_up`
  - `right_up`
  - `left_down`
  - `right_down`

If `direction` is either `horizontal` or `vertical` motion is absolute, i.e. the head is moved *to* the specified angle. Absolute motion uses centered coordinates in range [-90..90] for the horizontal orientation, and in range [-13..10] for the vertical orientation of the head. For all other values of `direction` motion is relative, i.e. the head is moved *by* the specified angle relative to the current orientation. For relative horizontal motion the angle must be in range [0..180], for relative vertical and diagonal movement the angle must be in range [0..25]. If `angle` is not specified for a cardinal relative direction, the head moves in that direction until it reaches the extreme horizontal or vertical orientation.
### command:head:turn
Instructs the robot to turn the head (horizontal move). The payload for the request is
```json
{
  "direction": (optional) one of ["left", "right"],
  "angle": (mandatory) absolute or relative angle
}
```
If `direction` is not specified motion is absolute, i.e. the head is moved *to* the specified horizontal angle. Absolute motion uses centered coordinates in range [-90..90]. If `direction` is specified, motion is relative and the head is moved *by* the specified angle. Relative horizontal angles must be in range [0..180].

### command:head:nod
Instructs the robot to nod the head (vertical move). The payload for this request is
```json
{
  "direction": (optional) one of ["up", "down"],
  "angle": (mandatory) absolute or relative angle
}
```
If `direction` is not specified motion is absolute, i.e. the head is moved *to* the specified vertical angle. Absolute motion uses centered coordinates in range [-13..10]. If `direction` is specified, motion is relative and the head is moved *by* the specified angle. Relative vertical angles must be in range [0..25].

### command:head:location
Instructs the robot to move the head to the absolute location. The payload for this request is
```json
{
  "hangle": (mandatory) absolute horizontal angle,
  "vangle": (mandatory) absolute vertical angle,
  "lock": (mandatory) lock mode, one of ["none", "horizontal", "vertical", "both"], default "none"
}
```
The location is specified by centered coordinates. Horizontal angles are in range [-90..90], vertical angles in range [-13..10].

### command:head:stop
Instructs the robot to stop moving the head. The payload for this request is empty.

### command:head:reset
Instructs the robot to move the head to its default position. The payload for this request is empty.

### command:head:center
Instructs the robot to lock the head in the central position. The payload for this request is empty.

### command:head:whitelight
Instructs the robot to switch the white light in the head on or off or set its brightness. The payload for this request is:
```json
{
  "on": (optional) flag specifying if light is switched on or off, one of [true, false],
  "brightness": (optional) 1 (least bright), 2 or 3 (most bright)
}
```

### command:head:led
Instructs the robot to control the LEDs in the robot head. The payload for this request is:
```json
{
  "side": (mandatory) one of ["left", "right"],
  "color": (mandatory) either "off", or one of ["white", "red", "green", "pink", "purple", "blue", "yellow"],
  "flicker": (optional) flicker delay time in units of 100ms, 0 to disable flicker mode,
  "random": (optional) number of colors in random mode, 0 to disable random mode
}
```

### command:arms:move
Instructs the robot to move one or both arms. The payload for this request is:
```json
{
  "side": (optional) one of ["left", "right", "both"],
  "direction": (optional) one of ["up", "down"],
  "angle": (optional) absolute or relative angle in range [0..270],
  "speed": (optional) speed in range [1..8], default 5
}
```
If `side` is not specified, both arms will move. If `angle` is not specified, `direction` is mandatory and one or both arms are moved in that direction until the extreme position is reached. If `angle` is specified but `direction` is not specified motion is absolute, i.e. the arm(s) move  *to* the specified angle. If both `angle` and `direction` are specified, the arm(s) move *by* the specified angle relative to the current position. Angles must be in the range [0..270], with 0 represents the arms pointing up, and 270 the arms pointing backwards.

### command:arms:stop
Instructs the robot to stop moving the arm(s). The payload for this request is
```json
{
  "side": (optional) one of ["left", "right", "both"]
}
```
If `side` is not specified both arms will stop moving.

### command:arms:reset
Instructs the robot to reset one or both arms to the default position. The payload for this request is
```json
{
  "side": (optional) one of ["left", "right", "both"],
  "speed": (optional) speed [1..8], default 5
}
```
If `side` is not specified both arms will be reset to the default position.

### command:arms:led
Instructs the robot to control the LEDs in the robot arms. The payload for this request is:
```json
{
  "side": (mandatory) one of ["left", "right"],
  "color": (mandatory) either "off", or one of ["white", "red", "green", "pink", "purple", "blue", "yellow"],
  "flicker": (optional) flicker delay time in units of 100ms, 0 to disable flicker mode,
  "random": (optional) number of colors in random mode, 0 to disable random mode
}
```

### command:led:set
Instructs the robot to control one or more LEDs. The payload for this request is:
```json
{
  "part": (mandatory) one of ["whitelight", "base", "left_head", "right_head", "left_arm", "right_arm", "all"],
  "on": (optional) flag specifying if light is switched on or off, one of [true, false],
  "brightness": (optional) 1 (least bright), 2 or 3 (most bright),
  "color": (optional) either "off", or one of ["white", "red", "green", "pink", "purple", "blue", "yellow"],
  "flicker": (optional) flicker delay time in units of 100ms, 0 to disable flicker mode,
  "random": (optional) number of colors in random mode, 0 to disable random mode
}
```
The `on` and `brightness` properties are only relevant if `part` equals `whitelight`. The `color`, `flicker` and `random` properties are only relevant for the other `part` values.

### command:face:emotion
Requests the robot to set the face emotion. The payload for this request is:
```json
{
  "emotion": (optional) emotion to display,
  "duration": (optional) duration of emotion in seconds,
  "refresh": (optional) refresh timer for emotion update
}
```

Supported values for `emotion` are
  - `angry`
  - `cry`
  - `excitement`
  - `faint`
  - `goodbye`
  - `grievance`
  - `kiss`
  - `laugher`
  - `normal`
  - `picknose`
  - `prise`
  - `question`
  - `shy`
  - `sleep`
  - `smile`
  - `snicker`
  - `speak`
  - `surprise`
  - `sweat`
  - `whistle`

If `emotion` is not specified the default emotion is set.  If no `duration` is specified or `duration` is equal to 0, the emotion will fall back to its default value after about ten seconds. If `duration` is specified and larger than 0, the emotion will be refreshed every `refresh` seconds to make the emotion persistent for the specified duration. The refresh time must be no longer than 30 seconds, if not specified it is set to 4 seconds.
### command:sensor:config
Instructs the robot to start or stop receiving events from one or more sensors. The payload for this request is:
```json
{
  "reset": (optional) reset parameters to default values, one of [true, false],
  "reload": (optional) reload parameters from configuration file, one of [true, false],
  "save": (optional) save updated parameters, one of [true, false],
  "all": one of [true, false] or { "enable: true/false, ... },
  "touch": one of [true, false] or { "enable: true/false },
  "orientation": one of [true, false] or { "enable": true/false, ... },
  "obstacle": one of [true, false] or { "enable: true/false },
  "pir": one of [true, false] or { "enable: true/false },
  "infrared": one of [true, false] or { "enable: true/false, ... },
  "voicelocate": one of [true, false] or { "enable: true/false },
}
```
If `reset` or `reload` is `true`, all sensor settings are reset to their defaults or reloaded from the configuration file, and the remaining sensor properties are ignored. `reset` takes precedence over `reload`. Otherwise, the specified parameters are passed to the sensor unit. If `save` is `true`, the updated settings are written to the configuration file; otherwise they remain active only until they are changed again or the bridge service restarts.

For each sensor or group of sensors, the parameter value can be a scalar value representing the enabled/disabled state, or a JSON object that contains an optional `enable` property that specifies the enabled/disabled state plus additional sensor-specific parameters. The scalar enabled/disabled state can be a boolean (`true`/`false`), number (1/0) or string ("true"/"false", "1"/"0", "enable"/"disable" or "enabled"/"disabled"). Sensor parameters are passed to the sensor unit as a Java `Map` instance that itself contains a `Map` instance for each sensor for which parameters are to be set. If the request payload contains a scalar enabled/disabled state the map for the sensor or group of sensors just contains the `enable` parameter, with the scalar enable/disable state converted to an actual boolean. If the request payload contains a JSON object value, the properties in that JSON object are copied to the map for that sensor or group of sensors. If the JSON object includes the `enable` property, the value is replaced by the matching boolean value.

### command:camera:config
Instructs the robot to update configuration parameters for the specified camera. The payload for this request is:
```json
{
  "camera": (optional) camera name, one of ["sanbot", "head", "android", "body", "orbbec", "3d"],
  "active": (optional) make specified camera the active camera, one of [true, false],
  "reset": (optional) reset all parameters to default values, one of [true, false],
  "reload": (optional) reload all parameters from configuration file, one of [true, false],
  "save": (optional) save updated parameters, one of [true, false],
  "<flex1>": (optional) flexible parameter,
  "<flex2>": (optional) flexible parameter,
    ...
}
```
The `camera` property is optional; if omitted, the configured default camera is used. The `head`, `body`, and `3d` values are aliases for the `sanbot`, `android`, and `orbbec` cameras. If `active` is `true`, the selected camera becomes the active camera. If `reset` or `reload` is `true`, camera parameters are reset to defaults or reloaded from the configuration file. Otherwise, the flexible parameters update the active camera settings. If `save` is `true`, the resulting settings are persisted; otherwise they remain active until changed again or the bridge service restarts.

### command:camera:reset
Instructs the robot to reset the specified camera. The payload for this request is
```json
{

  "camera": (optional) camera name, one of ["sanbot", "head", "android", "body", "orbbec", "3d"]
}
```
If `camera` is omitted, the configured default camera is reset. The `head`, `body`, and `3d` values are aliases for the `sanbot`, `android`, and `orbbec` cameras.

### command:camera:snapshot
Instructs the camera to capture a snapshot image as Base64-encoded data. The payload for this request is
```json
{
  "camera": (optional) camera name, one of ["sanbot", "head", "android", "body", "orbbec", "3d"],
  "<flex1>": (optional) flexible parameter,
  "<flex2>": (optional) flexible parameter,
    ...
}
```
If `camera` is omitted, the configured default camera is used. The `head`, `body`, and `3d` values are aliases for the `sanbot`, `android`, and `orbbec` cameras. A camera name may include a sensor suffix separated by a dash. The Orbbec camera supports `color`, `ir`, and `depth`; for example, `orbbec-ir` selects its infrared sensor.

The camera unit is responsible for updating the active camera parameters with the supplied flexible parameter values. The parameters will be semi-persistent and only remain active until they are updated again or the bridge service is restarted.

### command:camera:picture
Instructs the camera to capture a still image as Base64-encoded data. The payload for this request is
```json
{
  "camera": (optional) camera name, one of ["sanbot", "head", "android", "body", "orbbec", "3d"],
  "<flex1>": (optional) flexible parameter,
  "<flex2>": (optional) flexible parameter,
    ...
}
```
If `camera` is omitted, the configured default camera is used. The `head`, `body`, and `3d` values are aliases for the `sanbot`, `android`, and `orbbec` cameras. A camera name may include a sensor suffix separated by a dash. The Orbbec camera supports `color`, `ir`, and `depth`; for example, `orbbec-ir` selects its infrared sensor.

The camera unit is responsible for updating the active camera parameters with the supplied flexible parameter values. The parameters will be semi-persistent and only remain active until they are updated again or the bridge service is restarted.


### command:camera:face
Instructs the robot to return a cached face image as Base64-encoded data. The payload for this request is
```json
{
  "index": (optional) last-in, first-out index of image to retrieve, default 0,
  "<flex1>": (optional) flexible parameter,
  "<flex2>": (optional) flexible parameter,
  ...
}
```
The Sanbot camera unit is responsible for updating the active camera parameters with the supplied flexible parameter values. The parameters will be semi-persistent and only remain active until they are updated again or the bridge service is restarted.

### command:audio:volume
Instructs the robot to set the volume for a single Android audio stream. The payload for this request is
```json
{
  "stream": (optional) Android output stream,
  "volume": (mandatory) audio volume as percentage of maximum volume
}
```
Supported streams are `music`, `tts` , `system`, `alarm`, `voice_call`, `ring`, and `notification`.  If `stream` is not specified the volume for the default audio stream is set.

### command:audio:play
Instructs the robot to start playing audio from a URL or local audio file. The payload for this request is
```json
{
  "stream": (optional) Android output stream,
  "url": (optional) HTTP(S) audio URL,
  "filename": (optional) file name relative to SanbotBridge/audio
}
```

### command:audio:record
Instructs the robot to start recording audio. The payload for this request is
```json
{
  "duration": (optional) maximum recording duration in seconds,
  "save": (optional) one of [true, false, "filename"], default false
}
```
If `duration` is 0 or not specified the default value defined in the audio unit is applied. If `save` is `true`, the audio data will be saved to a file with a name constructed from the current date and time. If `save` has a string value that value is used as the filename. Specifying the file extension is optional, if not present a `wav` extension is added.

### command:audio:stop
Instructs the robot to stop audio playback or audio recording. The payload for this request is empty.

### command:audio:list
Instructs the robot to a list of available audio recordings.

### media:audio:remove
Instructs the robot to remove one or more audio recordings. The payload for this request is
```json
{
  "filename": (mandatory) "filename", or "all" to remove all audio recordings
}
```
Specifying the file extension is optional, if not present a `wav` extension is added.

### command:video:record
Instructs the robot to start recording video. The payload for this request is
```json
{
  "duration": (optional) maximum recording duration in seconds
}
```
If `duration` is 0 or not specified the default value defined in the video unit is applied. If `filename` is `null` or an empty string, a file name is generated from the current date and time. Specifying the file extension is optional, if not present a `rec` extension is added.

### command:video:stop
Instructs the robot to stop audio video recording. The payload for this request is empty.

### command:video:list
Instructs the robot to a list of available video recordings.

### media:video:remove
Instructs the robot to remove one or more video recordings. The payload for this request is
```json
{
  "filename": (mandatory) "filename", or "all" to remove all video recordings
}
```
Specifying the file extension is optional, if not present a `wav` extension is added.

### command:screen:image
Instructs the robot to show an image on the robot screen. The payload for this request is
```json
{
  "filename": (optional) image file name relative to the bridge image directory
}
```
The file must exist in the robot's `SanbotBridge/images` directory. If `filename` is not specified, the current image is removed from the screen, to display the main application window.

### command:speech:config
Instructs the robot to update the speech settings. The payload for this request is:
```json
{
  "reset": (optional) reset parameters to default values, one of [true, false],
  "reload": (optional) reload parameters from configuration file, one of [true, false],
  "save": (optional) save updated parameters, one of [true, false],
  "<flex1>": (optional) flexible parameter,
  "<flex2>": (optional) flexible parameter,
  ...
}
```
If `reset` or `reload` is `true`, text-to-speech parameters are reset to defaults or reloaded from the configuration file. Otherwise, the flexible parameters update the active text-to-speech settings. If `save` is `true`, the resulting settings are persisted; otherwise they remain active until changed again or the bridge service restarts.

### command:speech:say
Instructs the robot to start speaking. The payload for this request is
```json
{
  "text": (mandatory) phrase to say,
  "<flex1>": (optional) flexible parameter,
  "<flex2>": (optional) flexible parameter,
  ...
}
```
The text-to-speech unit is responsible for updating the active text-to-speech parameters with the supplied flexible parameter values. The parameters will be semi-persistent and remain active until they are updated again or the bridge service is restarted.

### command:speech:stop
Instructs the robot to stop speaking. The payload for this request is empty.

### command:speech:listen
Instructs the robot to start listening. The payload for this request is
```json
{
  "language": (optional) language code
}
```
If the language is not specified the current configured language will be used.

### command:battery:config
Instructs the robot to update the battery charging properties. The payload for this request is:
```json
{
    "mode": (mandatory) one of ["auto", "ondemand"],
    "level": (optional) minimum battery level, one of [0, 10, 20, 30, 40]
}
```
The minimum battery level is ignored for manual charging mode. If the value is not specified or equal to 0 the current minimum level is left unchanged.

### command:battery:charge
Instructs the robot to start moving or cancel moving to the charging pile. The payload for this request is
```json
{
  "cancel": (optional) one of [true, false]
}
```
If `cancel` is omitted, the robot moves to the charging pile. If `cancel` is `true`, that movement is cancelled; if it is `false`, no cancellation is requested. The value may also be supplied as a number (`1`/`0`) or string (`"true"`/`"false"`, `"1"`/`"0"`).

### command:script:upload
Instructs the robot to upload a script. The payload for this request is
```json
{
  "name": (mandatory) name under which to store the script,
  "content": (mandatory) string containing script data
}
```
The script name may contain only letters, numbers, and underscores. The service appends `.scr` and stores the UTF-8 file below `SanbotBridge/scripts` on external storage. Uploading an existing name replaces that script.

### command:script:start
Instructs the robot to start executing a script. The payload for this request is
```json
{
  "name": (mandatory) name of script to execute
}
```
The script name may contain only letters, numbers, and underscores. The `.scr` extension is not included in the request.

### command:script:stop
Instructs the robot to stop executing the current script. The payload for this request is empty.

### Script file format

Scripts are UTF-8 text files. Empty lines are ignored, and the complete script is parsed before execution starts. Supported instructions are:

- `execute <JSON object>` executes a bridge command. The JSON must contain `"request": "command:<module>:<action>"` and may contain `id` and `data`. The JSON object may span multiple lines.
- `pause <seconds>` pauses execution. Fractional, non-negative values with millisecond precision are accepted.
- `startloop <count>` begins a loop. A count of zero skips the loop body.
- `endloop` closes the nearest open loop. Loops may be nested.

Example:

```text
startloop 2
execute {"request":"command:robot:move","data":{"direction":"forward","speed":3,"duration":10}}
pause 0.5
execute {"request":"command:robot:stop","data":{}}
endloop
```

## Media requests

REST media requests return binary image, audio, or video-recording data. Successful equivalent WebSocket requests return a JSON metadata frame followed by one binary frame containing the media payload.

Sanbot `.rec` recordings are proprietary recording files, not directly playable video files. Use the included conversion utility to unpack the recording and create an MP4 file; Python 3 and FFmpeg on the system path are required:

```sh
python tools/unpack_sanbot_rec.py recording.rec recording.mp4 --frame-rate 20
```

The frame rate defaults to 20 frames per second when `--frame-rate` is omitted. Specify the actual recording rate when it differs.

### media:camera:snapshot
Instructs the robot to capture a snapshot image. The payload for this request is
```json
{
  "camera": (optional) camera name, one of ["sanbot", "head", "android", "body", "orbbec", "3d"],
  "<flex1>": (optional) flexible parameter,
  "<flex2>": (optional) flexible parameter,
  ...
}
```
If `camera` is omitted, the configured default camera is used. The `head`, `body`, and `3d` values are aliases for the `sanbot`, `android`, and `orbbec` cameras. A camera name may include a sensor suffix separated by a dash. The Orbbec camera supports `color`, `ir`, and `depth`; for example, `orbbec-ir` selects its infrared sensor.
The camera unit is responsible for updating the active camera parameters with the supplied flexible parameter values. The parameters will be semi-persistent and only remain active until they are updated again or the bridge service is restarted.

### media:camera:picture
Instructs the robot to capture a still image. The payload for this request is
```json
{
  "camera": (optional) camera name, one of ["sanbot", "head", "android", "body", "orbbec", "3d"],
  "<flex1>": (optional) flexible parameter,
  "<flex2>": (optional) flexible parameter,
  ...
}
```
If `camera` is omitted, the configured default camera is used. The `head`, `body`, and `3d` values are aliases for the `sanbot`, `android`, and `orbbec` cameras. A camera name may include a sensor suffix separated by a dash. The Orbbec camera supports `color`, `ir`, and `depth`; for example, `orbbec-ir` selects its infrared sensor.
The camera unit is responsible for updating the active camera parameters with the supplied flexible parameter values. The parameters will be semi-persistent and only remain active until they are updated again or the bridge service is restarted.

### media:camera:face
Instructs the robot to return a cached face image. The payload for this request is
```json
{
  "index": (optional) last-in, first-out index of image to retrieve, default 0,
  "<flex1>": (optional) flexible parameter,
  "<flex2>": (optional) flexible parameter,
  ...
}
```

### media:audio:get
Instructs the robot to retrieve an audio recording. The payload for this request is
```json
{
  "filename": (optional) file name relative to SanbotBridge/audio
}
```
If no filename is specified, the currently cached WAV recording is returned. The filename is relative to `SanbotBridge/audio`; `.wav` is added when the supplied name has no extension.

## Events

Events are asynchronous, server-initiated WebSocket messages. They are broadcast to every authorized WebSocket session, including read-only sessions; REST clients do not receive events. Event availability depends on the robot hardware, the selected bridge units, and configuration such as enabled sensor, face-detection, speech-recognition, and home-alarm support.

Every event has the following envelope. The event id is generated by the bridge and is independent of request ids. The top-level `timestamp` is the bridge's local time including its numeric UTC offset. The `data` property is omitted when an event has no additional data.

```json
{
  "id": "evt-1",
  "timestamp": "2026-09-24T10:15:30.125+0200",
  "type": "event",
  "module": "sensor",
  "event": "touch_pressed",
  "data": {
    "sensorId": 1,
    "sensorName": "right_chin",
    "touched": true
  }
}
```

Sensor events are emitted only when the corresponding sensor is enabled. Orientation and infrared events are filtered using their configured sensitivity; infrared events are also rate-limited by their configured update interval. Speech-recognition events require the Sanbot ASR unit. The `event:speech:speak` event is emitted by the Sanbot TTS unit; the Android TTS unit does not publish an equivalent callback event.

### event:robot:move

Indicates that the robot starts moving or changes direction. The event data is

```json
{
  "status": "status reported by the Sanbot SDK"
}
```

### event:robot:stop

Indicates that the robot stops moving. The event data is empty.

### event:robot:alarm

Indicates that the Sanbot home-alarm application reported an alarm. The event data is

```json
{
  "type": 2,
  "name": "intrusion"
}
```

The known alarm names are `obstruction`, `intrusion`, and `out-of-bounds`; unrecognized numerical types have the name `unknown`.

### event:led:whitelight

Returns the asynchronous result of `query:led:whitelight`. The event data is

```json
{
  "refid": "query-1",
  "brightness": 3
}
```

`refid` is present only when a reference id was supplied in the query.

### event:sensor:touch_pressed

Indicates that a touch sensor has been pressed. The event data is

```json
{
  "sensorId": 1,
  "sensorName": "right_chin",
  "touched": true
}
```

### event:sensor:touch_released

Indicates that a touch sensor has been released. The event data has the same shape as `event:sensor:touch_pressed`, with `touched` set to `false`.

### event:sensor:orientation

Indicates that the robot orientation changed sufficiently to pass the configured sensitivity threshold. The event data is

```json
{
  "timestamp": 1780215330,
  "yaw": 12.5,
  "pitch": -3.2,
  "roll": 1.0,
  "sensitivity": 90
}
```

The nested `timestamp` is expressed in Unix seconds and is separate from the formatted top-level event timestamp.

### event:sensor:obstacle_detected

Indicates that the obstacle detector reports that the robot is blocked. This event has no `data` property.

### event:sensor:obstacle_cleared

Indicates that the obstacle detector reports that the robot is no longer blocked. This event has no `data` property.

### event:sensor:pir_detected

Indicates that a passive infrared sensor detects motion. The event data is

```json
{
  "sensorId": 1,
  "sensorName": "pir_front",
  "detected": true
}
```

### event:sensor:pir_cleared

Indicates that a passive infrared sensor no longer detects motion. The event data has the same shape as `event:sensor:pir_detected`, with `detected` set to `false`.

### event:sensor:infrared

Provides aggregated active-infrared distance readings after the configured sensitivity threshold and update interval have been satisfied. The event data is

```json
{
  "timestamp": 1780215330,
  "sensorsFired": "1,2",
  "sensorData": [
    {
      "sensor": 1,
      "count": 4,
      "last": 20,
      "average": 22.5,
      "min": 18,
      "max": 28,
      "strength": 0.7666666666666667,
      "firstTimestamp": 1780215329,
      "lastTimestamp": 1780215330
    }
  ],
  "sensitivity": 90,
  "updateInterval": 1000,
  "strongest": {
    "sensorId": 1,
    "strength": 0.7666666666666667
  },
  "direction": {
    "x": 0.4,
    "y": 0.1,
    "label": "lower_center"
  }
}
```

The nested timestamps are expressed in Unix seconds. `strongest` and `direction` are present only when a strongest reading exists. The direction label combines `upper`, `middle`, or `lower` with `left`, `center`, or `right`; it is `none` when nothing is detected.

### event:sensor:voicelocate

Indicates the direction from which the robot detected a voice. The event data is

```json
{
  "angle": 45
}
```

### event:camera:stream_opened

Indicates that a shared live camera stream opened. The event data is

```json
{
  "handle": 1,
  "timestamp": 1780215330125,
  "capture": "capture-mode-id",
  "decode": "decode-mode-id"
}
```

The nested `timestamp` is expressed in Unix milliseconds. Sanbot camera events also contain `channel`; Orbbec camera events contain `sensor`.

### event:camera:stream_closed

Indicates that a shared live camera stream closed. The event data contains the fields from `event:camera:stream_opened` and the stream duration. If frames were received, it also contains the frame count and calculated frame rate.

```json
{
  "handle": 1,
  "timestamp": 1780215390125,
  "capture": "capture-mode-id",
  "decode": "decode-mode-id",
  "duration": "60000 ms",
  "frameCount": 900,
  "averageRate": "15fps"
}
```

Sanbot camera events use `frameRate` instead of `averageRate`. As with the open event, camera-specific `channel` or `sensor` data may also be present.

### event:camera:face

Indicates that Sanbot face detection produced a frame containing face data. The event data is

```json
{
  "faceCount": 1,
  "frameId": "frame-id",
  "face-1": {
    "left": 120,
    "top": 80,
    "right": 260,
    "bottom": 300
  }
}
```

`frameId` is optional. Each detected face adds a rectangle named `face-1`, `face-2`, and so on.

### event:audio:play

Indicates that audio playback started, completed, or failed. The event data is

```json
{
  "id": "playback-1",
  "source": "audio/example.mp3",
  "type": "file",
  "stream": "music",
  "status": "playing"
}
```

The event status is `playing`, `completed`, or `error`.

### event:audio:record

Indicates that an asynchronous audio recording completed. The event data is

```json
{
  "status": "completed",
  "size": 32044,
  "file": "/absolute/path/to/recording.wav"
}
```

`file` is present only when saving was requested. Its value is `<save_failed>` when the recording completed but saving failed.

### event:speech:awake

Indicates that the speech engine's wake/sleep state changed. The event data is

```json
{
  "awake": true
}
```

### event:speech:recognize_start

Indicates that speech recognition started. This event has no `data` property.

### event:speech:recognize_stop

Indicates that speech recognition stopped. This event has no `data` property.

### event:speech:recognize_text

Provides text returned by speech recognition. The event data is

```json
{
  "text": "recognized text",
  "engine": "recognition engine",
  "isLast": true
}
```

### event:speech:recognize_volume

Indicates that the reported speech-recognition volume changed. The event data is

```json
{
  "volume": 42
}
```

### event:speech:recognize_error

Indicates that speech recognition reported an error. The event data is

```json
{
  "code": 1,
  "subcode": 0
}
```

### event:speech:speak

Reports Sanbot speech-synthesis progress. The event data is

```json
{
  "id": 1,
  "engine": 0,
  "progress": 50
}
```

# Robot Scripts

Robot scripts provide a simple way to execute a sequence of bridge commands. Upload a script with `command:script:upload`, supplying a `name` and non-empty UTF-8 `content`. A name may contain only letters, numbers, and underscores; do not include a path or the `.scr` extension. The bridge stores the script as `SanbotBridge/scripts/<name>.scr` on external storage and replaces an existing script with the same name.

Use `info:script:list` to retrieve the available names. The response omits the `.scr` extension and sorts the names alphabetically. Use `command:script:start` with one of these names to execute it. The bridge reads and validates the complete file before starting the runner thread, so a missing, unreadable, or invalid script produces a failed start response before any command is executed.

The following instructions are supported:

- `startloop <count>` repeats its block exactly `count` times. The count must be a non-negative integer; zero skips the block. Loops may be nested.
- `endloop` closes the most recently opened loop and takes no argument.
- `execute {"request":"command:module:action","data":{...}}` executes a bridge command. The `request` value must contain exactly three non-empty components and must identify a `command` request. The optional `id` and `data` properties are passed to the command handler. The JSON object, including nested objects and arrays, may span multiple lines, but a quoted JSON string cannot contain a literal line break.
- `pause <seconds>` waits before executing the next instruction. The value must be non-negative and resolve to a whole number of milliseconds; for example, `pause 0.25` waits for 250 milliseconds.

Blank lines and indentation are allowed. Instruction keywords are case-insensitive. Comments and other keywords are not supported.

```text
startloop 3
    execute {
        "request": "command:arms:move",
        "data": {"side": "left", "angle": 5}
    }
    pause 10
endloop
execute {"request": "command:arms:reset"}
```

Commands execute sequentially: the runner waits for each service response.
Physical movement may continue after that response; use pause where needed.
A command response with a non-successful HTTP status stops the script and is written to the Android log under the `BridgeScriptRunner` tag with the relevant script line. File and syntax errors are returned by `command:script:start`. A successful start response only confirms that the runner thread started; the bridge does not currently publish a script-completion response or event.

`command:script:stop` requests cancellation and interrupts a pending pause.
An already dispatched command may finish. A new script can start once the previous
runner thread has exited. Stopping when no script is active also returns success.

# Demo Clients {#demo-clients}

The `client` directory contains four browser-based demo clients. They do not require an application build step. Open a client in a modern browser, make sure the computer can reach the robot, and replace the supplied robot address and API key where necessary. The bridge service must be running before a client can connect. The default bridge port is `8088`, and the default API key is `sanbot-bridge`. Each client provides navigation to the other tools and a light/dark mode control at the top-right; the selected theme is shared through browser local storage when available.

## REST console

Open `client/rest-console.html` to explore and invoke the REST API through a Swagger interface.

1. Enter the bridge base URL, for example `http://10.30.12.111:8088`, and select **Apply**.
2. Enter the configured API key and select **Apply**. Public helper and most info requests do not require it; `info:bridge:config`, command requests, and media requests do.
3. Expand an operation in the Swagger list, select **Try it out**, provide its parameters or JSON request data, and select **Execute**.

The page also provides direct status and head-light tests and an infrared-sensor heat map. It stores the selected base URL and API key in browser local storage when available. Because Swagger UI is loaded from `unpkg.com`, this console requires internet access when it is first loaded unless those assets are already cached by the browser.

## WebSocket console

Open `client/websocket-console.html` to send WebSocket requests and inspect responses, binary frames, and pushed events.

1. Select a robot target, enter the configured API key, and select **Connect**.
2. Select **Authorize** once the socket is connected. The first authorized active connection receives read-write access; additional connections are read-only.
3. Choose a request from **Quick Template**, adjust the generated JSON if needed, and send it.

The request builder covers the available info, query, command, script, and media requests. The traffic panel shows sent messages, responses, events, and binary-frame information. Continuous camera streaming remains available through the HTTP camera endpoints rather than the WebSocket connection.

## Camera console

Open `client/camera-console.html` for camera and media checks.

1. Enter the HTTP bridge URL and API key.
2. Select **Start Preview** to attach the MJPEG video stream, or **Open Feed Tab** to open the stream directly.
3. Use **Refresh Image** to retrieve a current camera image, **Refresh Face** to retrieve the latest cached face image, or **Check Status** to inspect the bridge status response.

The page displays the generated MJPEG URL so it can be copied into another compatible viewer. Camera selection and configuration are performed through the REST or WebSocket console.

## Dashboard console

Open `client/dashboard-console.html` for a simplified, button-oriented robot demonstration. This is the compiled, standalone dashboard and can be opened directly or copied without its source files.

Select **Connect**, then **Authorize**, before using the command buttons. **Show Log** displays WebSocket traffic, and the text field at the bottom sends a speech request. The editable page structure and connection settings are in `client/dashboard-console-source.html`. The title, labels, command buttons, and input action are defined in `client/dashboard-console-source.js`, which assigns its definitions to `window.SANBOT_DEMO_CONFIG`. Request templates may use `{{requestId}}`, `{{apiKey}}`, and `{{input}}`; the page replaces these placeholders before sending a request.

To create a single HTML file with the configuration embedded, run:

```powershell
.\client\dashboard-console-compile.bat
```

The script validates the source files and generates the standalone `client/dashboard-console.html`. Re-run it after changing either `dashboard-console-source.html` or `dashboard-console-source.js`.

# Version History
- **1.0.004 (9 Oct 2026)**
  - NEW: A @c flush parameter is added to Android text-to-speech @c say command to cancel current 
    and queued operations.
  - NEW: A @c nowait parameter is added to robot motion commands to immediately execute the command 
    as in application versions prior to 1.0.003. 
- **1.0.003 (5 Oct 2026)**
  - FIXED: The @e command:speech:say command does now correctly apply configured text-to-speech 
    values for parameters that are not explicitly specified in the request instead of falling back 
    to default parameters.
  - NEW: Robot motion commands are queued so the command is only executed if previous operations are
    either complted or timed out; 
  - CHANGED: The event:robot:wheels event is split: for all statuses not equal to 0 a new event
    @e event:robot:move is introduced with the status in the event data, for status 0 the event is 
    now @e event:robot:stop without any event data.
- **1.0.002 (26 Sep 20206)**
  - ADDED: Video recording (@e command:video:record, @e command:video:stop).
  - ADDED: Listing and removing audio and video recording files (@e command:video:list, 
    @e command:video:remove)
- **1.0.001 (24 Sep 2026)** 
  — Initial version.
