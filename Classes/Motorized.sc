MOTOR {
	classvar <ccMap, <instances, <>midiChannel=1, <>midiOut;
	var <type, <num, <midiController, single, <midiValue, <spec, <>func, <value, handle;
	*initClass {
		instances = MultiLevelIdentityDictionary.new;
		ccMap = IdentityDictionary[
			\transport -> IdentityDictionary[
				\backward -> 115,
				\forward  -> 116,
				\stop     -> 117,
				\play     -> 118,
				\loop     -> 120,
				\record   -> 119
			],
			\fader -> ([53]++(21..52)),
			\encoder -> (71..78),
			\pad -> (66..73)
		]
	}
	*new {| type=\fader, num=0, spec, function, single |
		var instance = instances[type, num];
		if (instance.isNil) {
			instance = super.newCopyArgs(
				type, num, ccMap[type][num], single
			).spec_(spec ?? \midi).func_(function).init;
			instances[type, num] = instance;
		} {
			if (spec.notNil) { instance.spec = spec };
			if (function.notNil) { instance.func = function }
		}
		^instance
	}
	*fader {| num=0, spec, function |
		^this.new(\fader, num, spec, function)
	}
	*encoder {| num=0, spec, function |
		^this.new(\encoder, num, spec, function)
	}
	*transport {| name, function |
		^this.new(\transport, name, \midi, function, single: true)
	}
	*backward {| function |
		^this.transport(\backward, function)
	}
	*forward {| function |
		^this.transport(\forward, function)
	}
	*stop {| function |
		^this.transport(\stop, function)
	}
	*play {| function |
		^this.transport(\play, function)
	}
	*loop {| function |
		^this.transport(\loop, function)
	}
	*record {| function |
		^this.transport(\record, function)
	}
	init {
		handle = MIDIFunc.cc({|midiValue|
			var newValue = spec.map(midiValue / 127.0);
			if (single or: newValue != value) {
				func.value(value = newValue, this)
			}
		}, midiController, midiChannel).permanent = true
	}
	set {|newValue|
		if (newValue != value) {
			this.value_(newValue);
			func.value(newValue, this)
		}
	}
	spec_ {|newSpec|
		spec = newSpec.asSpec;
		this.set(spec.default)
	}
	value_ {|newValue|
		var ccValue;
		ccValue = (spec.unmap(value = newValue) * 127).asInteger;
		if (midiOut.notNil and: { ccValue != midiValue }) {
			midiOut.control(midiChannel, midiController, ccValue)
		};
		midiValue = ccValue
	}
	connect {|key, argName|
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
		^[type, num, Spec.specs.findKeyForValue(spec) ?? spec, func]
	}
	free {
		handle.free;
		instances.removeAt(type, num);
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
	storeArgs { ^[type,num,Spec.specs.findKeyForValue(spec) ?? spec,func] }
 	free {
		routine.stop;
		instances.removeAt(type,num);
		super.free;
	}
}
