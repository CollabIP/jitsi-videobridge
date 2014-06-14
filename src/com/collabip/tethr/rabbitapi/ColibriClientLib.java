package com.collabip.tethr.rabbitapi;

import java.io.FileReader;

import org.jitsi.util.Logger;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Exposes JavaScript functions from the browser-side client
 * library. 
 * @author chaz
 * 
 */

public class ColibriClientLib extends Thread{

	public final static ColibriClientLib instance = new ColibriClientLib();
	
	private static final Logger _logger = Logger.getLogger(ColibriClientLib.class);
	
	private Context _cx;
	private Scriptable _scope;
	
	private static enum COMMAND {SDPTOIQ};
	
	private class QueueItem
	{
		public COMMAND command;
		public Object functionArgs[];
		public String result;
	}
	
	private Map<COMMAND, Function> _functions;
	
	private BlockingQueue<QueueItem> _queue = new LinkedBlockingQueue<QueueItem>();
	
	private ColibriClientLib() 
	{
		this.start();
	}
	
	public static ColibriClientLib getInstance()
	{
		return instance;
	}
	
	public String SdpToIq(String sdp) throws InterruptedException
	{
		QueueItem item = new QueueItem();
		item.command = COMMAND.SDPTOIQ;
		item.functionArgs = new Object[]{ sdp };
		_queue.add(item);
		synchronized(item)
		{
			item.wait();			
		}
		return item.result;
	}
	
	@Override
	public void run() {
		
		try 
		{
			_cx = ContextFactory.getGlobal().enterContext();
			_cx.setOptimizationLevel(-1);
			_cx.setLanguageVersion(Context.VERSION_1_5);
			
			_scope=_cx.initStandardObjects();
			_cx.evaluateString(_scope, "var print=function(value){java.lang.System.out.println(value)};", "setup", 1, null);
			_cx.evaluateReader(_scope, new FileReader("js/env.rhino.1.2.js"), "envjs", 1, null);
			
			_cx.evaluateReader(_scope, new FileReader("js/strophe/strophe.jingle.adapter.js"), "strophe.jingle.adaptor", 1, null);
			_cx.evaluateReader(_scope, new FileReader("js/strophe/strophe.jingle.bundle.js"), "strophe.jingle.bundle", 1, null);
			_cx.evaluateReader(_scope, new FileReader("js/strophe/strophe.jingle.js"), "strophe.jingle", 1, null);
			_cx.evaluateReader(_scope, new FileReader("js/strophe/strophe.jingle.sdp.js"), "strophe.jingle.sdp", 1, null);
			_cx.evaluateReader(_scope, new FileReader("js/strophe/strophe.jingle.sdp.util.js"), "strophe.jingle.sdp.util", 1, null);
			_cx.evaluateReader(_scope, new FileReader("js/strophe/strophe.jingle.sessionbase.js"), "strophe.jingle.sessionbase", 1, null);
			_cx.evaluateReader(_scope, new FileReader("js/strophe/strophe.jingle.session.js"), "strophe.jingle.session", 1, null);
			_cx.evaluateReader(_scope, new FileReader("js/colibri/colibri.focus.js"), "colibri.focus", 1, null);
			_cx.evaluateReader(_scope, new FileReader("js/colibri/colibri.session.js"), "colibri.session", 1, null);
			
			_cx.evaluateReader(_scope, new FileReader("js/colibri-if.js"), "colibri-if", 1, null);
					
			_functions = new HashMap<COMMAND, Function>();
			
			Object fObj = _scope.get("SdpToIq", _scope);
			if(!(fObj instanceof Function))
			{
				throw new Exception("SdpToIq not defined as function in javascript stuff.");
			}
			else
			{
				_functions.put(COMMAND.SDPTOIQ, (Function)fObj);
			}
			
			while(true)
			{
				QueueItem item = _queue.take();
				synchronized(item)
				{
					Object result = _functions.get(item.command).call(_cx, _scope, _scope, item.functionArgs);
					item.result = Context.toString(result);
					item.notify();
				}
			}	
			
		} catch (Exception e)
		{
			e.printStackTrace();
		}		
	}
	
}
