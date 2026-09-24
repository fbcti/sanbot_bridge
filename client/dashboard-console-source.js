// Edit labels, button text, and JSON payload templates here.
// Supported placeholders: {{requestId}}, {{apiKey}}, and {{input}}.
window.SANBOT_DEMO_CONFIG = {
    title: 'Robot Demo',
    topRow: {
        connectText: 'Connect',
        authorizeText: 'Authorize'
    },
    rows: [
        {
            label: 'Head',
            buttons: [
                {
                    text: 'Turn Left',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'head',
                        action: 'turn',
                        data: {
                            direction: 'left',
                            angle: 90
                        }
                    }
                },
                {
                    text: 'Turn Right',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'head',
                        action: 'turn',
                        data: {
                            direction: 'right',
                            angle: 90
                        }
                    }
                },
                {
                    text: 'Nod Up',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'head',
                        action: 'nod',
                        data: {
                            direction: 'up',
                            angle: 15
                        }
                    }
                },
                {
                    text: 'Nod Down',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'head',
                        action: 'nod',
                        data: {
                            direction: 'down',
                            angle: 15
                        }
                    }
                },
                {
                    text: 'Head Stop',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'head',
                        action: 'stop',
                        data: {}
                    }
                },
                {
                    text: 'Head Reset',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'head',
                        action: 'reset',
                        data: {}
                    }
                }
            ]
        },
        {
            label: 'Face',
            buttons: [
                {
                    text: 'Angry',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'face',
                        action: 'emotion',
                        data: {
                            emotion: 'angry',
                            duration: 30
                        }
                    }
                },
                {
                    text: 'Surprise',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'face',
                        action: 'emotion',
                        data: {
                            emotion: 'surprise',
                            duration: 30
                        }
                    }
                },
                {
                    text: 'Laugh',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'face',
                        action: 'emotion',
                        data: {
                            emotion: 'laughter',
                            duration: 30
                        }
                    }
                },
                {
                    text: 'Cry',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'face',
                        action: 'emotion',
                        data: {
                            emotion: 'cry',
                            duration: 30
                        }
                    }
                },
                {
                    text: 'Smile',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'face',
                        action: 'emotion',
                        data: {
                            emotion: 'smile',
                            duration: 30
                        }
                    }
                },
                {
                    text: 'Normal',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'face',
                        action: 'emotion',
                        data: {
                            emotion: 'normal',
                            duration: 3600
                        }
                    }
                }
            ]
        },
        {
            label: 'Arms',
            buttons: [
                {
                    text: 'Left Up',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'arms',
                        action: 'move',
                        data: {
                            side: 'left',
                            direction: 'up'
                        }
                    }
                },
                {
                    text: 'Right Up',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'arms',
                        action: 'move',
                        data: {
                            side: 'right',
                            direction: 'up'
                        }
                    }
                },
                {
                    text: 'Both Up Slow',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'arms',
                        action: 'move',
                        data: {
                            side: 'both',
                            speed: 1,
                            direction: 'up',
                            angle: 30
                        }
                    }
                },
                {
                    text: 'Both Down',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'arms',
                        action: 'move',
                        data: {
                            side: "both",
                            angle: 180
                        }
                    }
                },
                {
                    text: 'Stop',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'arms',
                        action: 'stop',
                        data: {
                            part: 'both'
                        }
                    }
                },
                {
                    text: 'Reset',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'arms',
                        action: 'reset',
                        data: {
                            part: 'both'
                        }
                    }
                }
            ]
        },
        {
            label: 'Light',
            buttons: [
                {
                    text: 'Head Light',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'head',
                        action: 'whitelight',
                        data: {
                            on: true,
                            brightness: 3
                        }
                    }
                },
                {
                    text: 'Leds Red',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'led',
                        action: 'set',
                        data: {
                            part: 'all',
                            color: 'red'
                        }
                    }
                },
                {
                    text: 'Leds Blue',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'led',
                        action: 'set',
                        data: {
                            part: 'all',
                            color: 'blue'
                        }
                    }
                },
                {
                    text: 'Leds Green Flicker',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'led',
                        action: 'set',
                        data: {
                            part: 'all',
                            color: 'green',
                            flicker: 5
                        }
                    }
                },
                {
                    text: 'Leds Random',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'led',
                        action: 'set',
                        data: {
                            part: 'all',
                            flicker: 2,
                            random: 7
                        }
                    }
                },
                {
                    text: 'Leds Off',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'led',
                        action: 'set',
                        data: {
                            part: 'all',
                            color: 'off'
                        }
                    }
                }
            ]
        },
        {
            label: 'Speech',
            buttons: [
                {
                    text: 'English',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'speech',
                        action: 'config',
                        data: {
                            language: 'english'
                        }
                    }
                },
                {
                    text: 'German',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'speech',
                        action: 'config',
                        data: {
                            language: 'german'
                        }
                    }
                },
                {
                    text: 'Dutch',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'speech',
                        action: 'config',
                        data: {
                            language: 'dutch'
                        }
                    }
                },
                {
                    text: 'Slow',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'speech',
                        action: 'config',
                        data: {
                            speed: 10
                        }
                    }
                },
                {
                    text: 'Fast',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'speech',
                        action: 'config',
                        data: {
                            speed: 200
                        }
                    }
                },
                {
                    text: 'Reset',
                    payload: {
                        id: '{{requestId}}',
                        type: 'command',
                        module: 'speech',
                        action: 'config',
                        data: {
                            reset: true
                        }
                    }
                }
            ]
        }
    ],
    inputRow: {
        label: 'Speech Text',
        inputPlaceholder: 'Type text for the robot',
        buttonText: 'Say Text',
        payload: {
            id: '{{requestId}}',
            type: 'command',
            module: 'speech',
            action: 'say',
            data: {
                text: '{{input}}'
            }
        }
    }
};

