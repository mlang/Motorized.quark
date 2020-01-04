MOTOR {
	classvar <msgNumMap, <instances, <>midiOut;
	var <type, <msgNum, <chan, <msgType, <maxMsgNum, single, <midiValue, <spec, <>func, <value, handle;

	*initClass {
		instances = MultiLevelIdentityDictionary.new;
		msgNumMap = MultiLevelIdentityDictionary.new;
		msgNumMap[\button, \backward] = 115;
		msgNumMap[\button, \forward] = 116;
		msgNumMap[\button, \stop] = 117;
		msgNumMap[\button, \play] = 118;
		msgNumMap[\button, \loop] = 120;
		msgNumMap[\button, \record] = 119;
		msgNumMap[\fader, \master] = 53;
		32.do {|i|
			msgNumMap[\fader, i+1] = 21+i;
			msgNumMap[\encoder, i+1] = 71+i;
			msgNumMap[\padOn, i+1] = 66+i;
			msgNumMap[\padOff, i+1] = 66+i;
		};
		msgNumMap[\modwheel] = 1;
	}
	*isMIDIEndPoint {
		^{| ep |
			ep.device == "MOTÖR49 Keyboard" and:
			ep.name == "MOTÖR49 Keyboard MIDI 1"
		}
	}
	*new {| type=#[\fader, \master], spec, action, single=false |
		var instance = instances.atPath(type);
		type = type.asArray;
		if (instance.isNil) {
			var msgNum = msgNumMap.atPath(type);
			var chan = if(#[
				\fader, \encoder, \button, \padOn, \padOff
			].includes(type[0]), 1, 0);
			var msgType = if(#[\fader, \encoder, \button, \modwheel].includes(type[0]),
				\control,
				type[0].switch(
					\padOn, \noteOn,
					\padOff, \noteOff,
					type[0]
				)
			);
			var maxMsgNum = switch(type[0],
				\padOn, 100,
				\padOff, 100,
				\bend, 16383,
				127
			);
			instance = super.newCopyArgs(
				type, msgNum, chan, msgType, maxMsgNum, single
			).spec_(spec ?? \midi).func_(action).init;
			instances.putAtPath(type, instance);
		} {
			if (action.notNil) { instance.func = action };
			if (spec.notNil) { instance.spec = spec };
		}
		^instance
	}
	*fader {| name=\master, spec, action |
		^this.new([fader: name], spec, action)
	}
	*encoder {| name=1, spec, action |
		^this.new([encoder: name.asInteger], spec, action)
	}
	*button {| name, action |
		^this.new([button: name], nil, action, single: true)
	}
	*backward {| action | ^this.button(\backward, action) }
	*forward  {| action | ^this.button(\forward, action) }
	*stop     {| action | ^this.button(\stop, action) }
	*play     {| action | ^this.button(\play, action) }
	*loop     {| action | ^this.button(\loop, action) }
	*record   {| action | ^this.button(\record, action) }
	*padOn {| name=1, spec, function |
		^this.new([padOn: name.asInteger], spec, function, single: true)
	}
	*padOff {| name=1, spec, function |
		^this.new([padOff: name.asInteger], spec, function, single: true)
	}
	*noteOn {| spec, function |
		^this.new(\noteOn, spec, function, single: true)
	}
	*noteOff {| action |
		^this.new(\noteOff, nil, action, single: true)
	}
	*bend {| spec, action |
		^this.new(\bend, spec, action)
	}
	*modwheel {| spec, action |
		^this.new(\modwheel, spec, action)
	}
	init {
		handle = MIDIFunc.new({
			arg midiValue, midiNumber;
			var newValue = spec.map(midiValue / maxMsgNum);
			if (single or: { newValue != value }) {
				value = newValue;
				this.changed(\value);
				func.value(value, midiNumber, this)
			}
		}, msgNum, chan, msgType).permanent = true
	}
	disable { handle.disable }
	enable { handle.enable }
	
	set {| newValue |
		if (newValue != value) {
			this.value_(newValue);
			func.value(newValue, msgNum, this)
		}
	}
	spec_ {| newSpec |
		spec = newSpec.asSpec;
		if (single.not, {
			this.set(spec.default)
		})
	}
	value_ {|newValue|
		var ccValue = (spec.unmap(value = newValue) * maxMsgNum).asInteger;
		this.changed(\value);
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
			Group, {
				func = {|val| key.set(argName, val) }
			},
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
			Spec.specs.findKeyForValue(spec) ?? spec,
			func,
			single
		]
	}
	free {
		handle.free;
		instances.removeAt(*type);
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
