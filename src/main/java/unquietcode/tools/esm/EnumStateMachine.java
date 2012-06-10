/*******************************************************************************
 Copyright 2011 Benjamin Fagin

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


    Read the included LICENSE.TXT for more information.
 ******************************************************************************/

package unquietcode.tools.esm;

import unquietcode.tools.esm.EnumStringParser.Value;

import java.util.*;


/**
 * @author  Benjamin Fagin
 * @version 12-23-2010
 *
 * Null is a valid state in this system. The initial starting point is null by default.
 */
public class EnumStateMachine {
	private Map<Enum, State> states = new HashMap<Enum, State>();
	private State initial;
	private State current;
	int transitions;


	public EnumStateMachine() {
		initial = null;
		current = initial;
		transitions = 0;
	}

	public EnumStateMachine(Enum initial) {
		this.initial = getState(initial);
		current = this.initial;
		transitions = 0;
	}

	public EnumStateMachine(String configuration) throws ParseException {
		configureByString(configuration);
	}

	public void reset() {
		transitions = 0;
		current = initial;
	}

	public boolean transition(Enum state) {
		State next = getState(state);
		if (!current.transitions.containsKey(next)) {
			throw new TransitionException("No transition exists between "+ current +" and "+ next);
		}

		Transition transition = current.transitions.get(next);
		for (StateMachineCallback callback : transition.callbacks) {
			callback.performAction();
		}

		transitions += 1;

		if (current == next) {
			return false;
		} else {
			current = next;
			return true;
		}
	}

	public Enum state() {
		return current.theEnum;
	}

	public Enum getInitialState() {
		return initial.theEnum;
	}

	/**
	 * Will not reset, just sets the initial state.
	 *
	 * @param state initial state to be set after next reset
	 */
	public void setInitialState(Enum state) {
		initial = getState(state);
	}

	public void addTransitions(Enum fromState, Enum...toStates) {
		addTransitions(fromState, null, toStates);
	}

	/**
	 * Add a transition from one state to 0..n other states. The callback
	 * will be executed as the transition is occurring.
	 */
	public void addTransitions(Enum fromState, StateMachineCallback callback, Enum...toStates) {
		Set<Enum> set = makeSet(toStates);
		State from = getState(fromState);

		for (Enum anEnum : set) {
			State to = getState(anEnum);
			Transition transition;

			if (from.transitions.containsKey(to)) {
				transition = from.transitions.get(to);
			} else {
				transition = new Transition(to);
				from.transitions.put(to, transition);
			}

			if (callback != null) {
				transition.callbacks.add(callback);
			}
		}
	}

	public void setTransitions(Enum fromState, Enum...toStates) {
		State state = getState(fromState);
		state.transitions.clear();
		addTransitions(fromState, toStates);
	}

	private Set<Enum> makeSet(Enum states[]) {
		Set<Enum> set = new HashSet<Enum>();

		if (states == null) {
			set.add(null);
		} else {
			set.addAll(Arrays.asList(states));
		}

		return set;
	}

	/**
	 * For every enum in the class, creates a transition between that enum and the others.
	 * If includeSelf is true, the enums are allowed to transition back to themselves.
	 *
	 * @param clazz         The class of the enum to add.
	 * @param includeSelf   True if enums are allowed to transition to themselves.
	 */
	public void addAll(Class clazz, boolean includeSelf) {
		if (clazz == null || !clazz.isEnum()) {
			throw new IllegalArgumentException("A valid enum class must be provided.");
		}

		Enum[] full = (Enum[]) clazz.getEnumConstants();

		if (includeSelf) {
			for (Enum e : full) {
				addTransitions(e, full);
			}
		} else {
			for (Enum e : full) {
				addTransitions(e, full);

				State self = getState(e);
				self.transitions.remove(self);
			}
		}
	}

// TODO fix this up
//	@Override
//	public boolean equals(Object o) {
//		if (o.getClass() != this.getClass())
//			return false;
//
//		EnumStateMachine other = (EnumStateMachine) o;
//
//		if (states.size() != other.states.size()) {
//			return false;
//		}
//
//		for (Map.Entry<Enum, Set<Enum>> entry : states.entrySet()) {
//			if (!other.states.containsKey(entry.getKey()))
//				return false;
//
//			Set<Enum> tSet = entry.getValue();
//			Set<Enum> oSet = other.states.get(entry.getKey());
//
//			if (tSet.size() != oSet.size())
//				return false;
//
//			for (Enum e : tSet) {
//				if (!oSet.contains(e))
//					return false;
//			}
//		}
//
//		return true;
//	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		if (initial != null) {
			sb.append(fullString(initial.theEnum)).append(" | \n");
		}

		int i = 1;
		for (Map.Entry<Enum, State> entry : states.entrySet()) {
			sb.append("\t").append(fullString(entry.getKey())).append(" : {");

			int j = 1;
			for (Transition t : entry.getValue().transitions.values()) {
				sb.append(fullString(t.next.theEnum));

				if (j++ != entry.getValue().transitions.size()) {
					sb.append(", ");
				}
			}

			sb.append("}");

			if (i++ != states.size())
				sb.append(" | \n");
		}

		return sb.toString();
	}

	private State getState(Enum token) {
		if (states.containsKey(token)) {
			return states.get(token);
		}

		State s = new State(token);
		states.put(token, s);
		return s;
	}

	private static class State {
		final Enum theEnum;
		final Map<State, Transition> transitions = new HashMap<State, Transition>();

		State(Enum theEnum) {
			this.theEnum = theEnum;
		}

		public @Override boolean equals(Object obj) {
			if (!(obj instanceof State)) { return false; }
			State other = (State) obj;

			if (this.theEnum == null) {
				return other.theEnum == null;
			} else {
				return this.theEnum.equals(other.theEnum);
			}
		}
	}

	private static class Transition {
		final State next;
		final Set<StateMachineCallback> callbacks = new HashSet<StateMachineCallback>();

		Transition(State next) {
			this.next = next;
		}

//		public @Override boolean equals(Object obj) {
//			if (!(obj instanceof Transition)) { return false; }
//			Transition other = (Transition) obj;
//
//			if (this.next.theEnum == null) {
//				return other.next.theEnum == null;
//			} else {
//				return this.next.theEnum.equals(other.next.theEnum);
//			}
//		}
	}

	//==o==o==o==o==o==o==| String Configuration |==o==o==o==o==o==o==//

	enum Token {
		@Value("{")
		SET_START,

		@Value("}")
		SET_END,

		@Value("|")
		DIVIDER,

		@Value(":")
		NAME_END,

		@Value(",")
		COMMA;
	}

	/**
	 * Basic form is:
	 * "state1 : {transition1, transition2}, state2 : {transition3, transition4}, state3 : {}"
	 *
	 * Listing empty sets is optional. Can also add the initial state to the front like so:
	 * "initial | state1 : {transition1, transition2} | state2 : {transition3, transition4}"
	 *
	 * The states are the class strings of the enums being used. They will be processed via reflection.
	 * The existing information is preserved; used clear() prior to avoid that.
	 * When the operation is complete, the state machine is reset.
	 *
	 * @param   string   configuration string
	 * @throws  ParseException  if the configuration string is malformed
	 */
	public void configureByString(String string) throws ParseException {
		EnumStringParser parser = new EnumStringParser(string.trim());

		String initialString = parser.getString(Token.DIVIDER);
		if (initialString != null) {
			initial = getState(instantiate(initialString));
		}

		while (!parser.isEmpty()) {
			String name = parser.getString(Token.NAME_END);
			parser.chomp(Token.SET_START);
			String elements[] = parser.getStrings(Token.SET_END, Token.COMMA);
			parser.chomp(Token.DIVIDER);

			if (name == null || elements == null) {
				throw new ParseException("Malformed configuration string.");
			}

			State state = getState(instantiate(name));

			for (String e : elements) {
				State next = getState(instantiate(e));

				if (!state.transitions.containsKey(next)) {
					Transition t = new Transition(next);
					state.transitions.put(next, t);
				}
			}
		}

		reset();
	}

	@SuppressWarnings("unchecked")
	private Enum instantiate(String string) throws ParseException {
		if (string.equals("null"))
			return null;

		int dot = string.lastIndexOf(".");

		if (dot == -1  ||  dot+1 == string.length()) {
			throw new ParseException("Invalid class string: " + string);
		}

		String front = string.substring(0, dot).trim();
		String back = string.substring(dot+1).trim();
		Enum e = null;

		try {
			Class c = Class.forName(front);
			e = Enum.valueOf(c, back);
		} catch (ClassNotFoundException ex) {
			throw new ParseException("Invalid class string: " + front);
		}

		return e;
	}

	public String fullString(Enum e) {
		if (e == null)
			return null;

		return e.getClass().getName() + "." + e.toString();
	}
}





//TODO some kind of listener system that allows the user to respond to transitions
/*
	something like
		addListener(Enum state1, Enum state2, Listener listener)

	then, whenever a transition from state1 to state2 occurs, the lister will be notified with
	some sort of event.

	So either use the built-in property-change listener system, or make a new system.
	(better to just work with the existing stuff). Could use the weaklistener wrapepr from
	PaperTrail to prevent lapsed listeners.
*/