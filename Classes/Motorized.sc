MOTOR {
	classvar <msgNumMap, <instances, <>midiOut;
	var <type, <num, <msgNum, <chan, <msgType, <maxMsgNum, single, <midiValue, <spec, <>func, <value, handle;

	*initClass {
		instances = MultiLevelIdentityDictionary.new;
		msgNumMap = (
			button: (
				backward: 115,
				forward: 116,
				stop: 117,
				play: 118,
				loop: 120,
				record: 119
			),
			fader: (
				master: 53
			).putEach((1..32), (21..52)),
			encoder: ().putEach((1..32), (71..102)),
			pad: ().putEach((1..32), (66..97))
		)
	}
	*new {| type=\fader, num=1, spec=\midi, function, single=false |
		var instance = instances[type, num ? \all];
		if (instance.isNil) {
			instance = super.newCopyArgs(
				type,
				num,
				// msgNum
				num !? {
					msgNumMap [
						#[\padOn, \padOff].includes(type).if(\pad, type)
					] [
						num
					]
				},
				// chan
				#[
					\fader, \encoder, \button, \padOn, \padOff
				].includes(type).if(1, 0),
				// msgType
				#[\fader, \encoder, \button].includes(type).if(
					\control,
					type.switch(
						\padOn, \noteOn,
						\padOff, \noteOff,
						type
					)
				),
				// maxMsgNum
				type.switch(
					\padOn, 100,
					\padOff, 100,
					127
				),
				single
			).spec_(spec).func_(function).init;
			instances[type, num ? \all] = instance;
		} {
			if (spec.notNil) { instance.spec = spec };
			if (function.notNil) { instance.func = function }
		}
		^instance
	}
	*fader {| name=\master, spec=\midi, function |
		^this.new(\fader, name, spec, function)
	}
	*encoder {| name=1, spec=\midi, function |
		^this.new(\encoder, name, spec, function)
	}
	*button {| name, function |
		^this.new(\button, name, \midi, function, single: true)
	}
	*backward {| function |
		^this.button(\backward, function)
	}
	*forward {| function |
		^this.button(\forward, function)
	}
	*stop {| function |
		^this.button(\stop, function)
	}
	*play {| function |
		^this.button(\play, function)
	}
	*loop {| function |
		^this.button(\loop, function)
	}
	*record {| function |
		^this.button(\record, function)
	}
	*padOn {| num=1, spec=\midi, function |
		^this.new(\padOn, num, spec, function, single: true)
	}
	*padOff {| num=1, spec=\midi, function |
		^this.new(\padOff, num, spec, function, single: true)
	}
	*noteOn {| spec=\midi, function |
		^this.new(\noteOn, nil, spec, function, single: true)
	}
	*noteOff {| function |
		^this.new(\noteOff, nil, \midi, function, single: true)
	}
	init {
		handle = MIDIFunc.new({
			arg midiValue, midiNumber;
			var newValue = spec.map(midiValue / maxMsgNum);
			if (single or: { newValue != value }) {
				func.value(value = newValue, midiNumber, this)
			}
		}, msgNum, chan, msgType).permanent = true
	}
	set {| newValue |
		if (newValue != value) {
			this.value_(newValue);
			func.value(newValue, msgNum, this)
		}
	}
	spec_ {| newSpec |
		spec = newSpec.asSpec;
		this.set(spec.default)
	}
	value_ {|newValue|
		var ccValue = (spec.unmap(value = newValue) * maxMsgNum).asInteger;
		if (
			{ msgType == \control }.value and:
			midiOut.notNil and:
			{ ccValue != midiValue }
		) {
			midiOut.control(chan, msgNum, ccValue)
		};
		midiValue = ccValue
	}
	connect {| key, argName |
		key.class.switch(
			NodeProxy, {
				func = {|val| key.set(argName, val) }
			},
			Synth, {
				func = {|val| key.set(argName, val) }
			}
		)
	}
	storeArgs {
		^[
			type,
			num,
			Spec.specs.findKeyForValue(spec) ?? spec,
			func,
			single
		]
	}
	free {
		handle.free;
		instances.removeAt(type, num ? \all);
		super.free;
	}
}

// Untested since my BCF2000 died.
BCF2000 {
	classvar <ccMap, <>midiChannel=0, <>midiOut, <instances, <>proxyspace;
	var <type, <num, <midiController, <midiValue, <spec, <>func, <value, routine;

	*initClass {
		instances = MultiLevelIdentityDictionary.new;
		ccMap = IdentityDictionary[
			\key -> ((65..80)++(89..92)), \slider -> (81..88),
			// rotary groups 1-4
			\r1 -> (1..8), \r2 -> (9..16), \r3 -> (17..24), \r4 -> (25..32),
			// rotary push groups 1-4
			\p1 -> (33..40), \p2 -> (41..48), \p3 -> (49..56), \p4 -> (57..64)
		]
	}
	*new {|type=\slider, num=1, spec, function|
		var instance;
		instance = instances[type, num];
		if (instance.isNil) {
			instance = super.newCopyArgs(type,num,ccMap[type][num-1]).spec_(spec ?? \midi).func_(function).init;
			instances[type, num] = instance;
		} {
			if (spec.notNil) { instance.spec = spec };
			if (function.notNil) { instance.func = function }
		};
		^instance
	}
	init {
		routine = Routine {
			var event, newValue;
			loop {
				event = MIDIIn.waitControl(nil, midiChannel, midiController);
				midiValue = event.ctlval;
				newValue = spec.map(midiValue / 127.0);
				if (newValue != value) {
					func.value(value = newValue, this)
				}
			}
		}.play
	}
	spec_ {|newSpec| spec = newSpec.asSpec; this.value = spec.default }
	set {|newValue|
		if (newValue != value) {
			this.value_(newValue);
			func.value(newValue, this)
		}
	}
	value_ {|newValue|
		var ccValue;
		ccValue = (spec.unmap(value = newValue)*127).asInteger;
		if (midiOut.notNil and: { ccValue != midiValue }) {
			midiOut.control(midiChannel, midiController, ccValue)
		};
		midiValue = ccValue
	}
	connect {|key, argName|
		key.class.switch(
			Symbol, {
				this.func = {|val| BCF.proxyspace[key].set(argName, val) }
			},
			NodeProxy, {
				this.func = {|val| key.set(argName, val) }
			},
			Synth, {
				this.func = {|val| key.set(argName, val) }
			}
		)
	}
	storeArgs {
		^[
			type,
			num,
			Spec.specs.findKeyForValue(spec) ?? spec,
			func
		]
	}
 	free {
		routine.stop;
		instances.removeAt(type,num);
		super.free;
	}
}
