package com.collabip.tethr.rabbitapi;

public class RabbitApi {
	
	/**
	 * Prefix to use for config file item names.
	 */
	public static final String CONFIG_PNAME = "com.collabip.tethr.rabbitapi.";
	
	/**
	 * Queue name for incoming rabbit messages.
	 */
	public static final String CREATE_QUEUE_PNAME = CONFIG_PNAME + "CREATE_QUEUE_NAME";
	public static final String ICE_CANDIDATE_QUEUE_PNAME = CONFIG_PNAME + "ICE_CANDIDATE_QUEUE_NAME";
	public static final String OFFER_QUEUE_PNAME = CONFIG_PNAME + "OFFER_QUEUE_NAME";
	
	
	/**
	 * Exchange name for outgoing rabbit messages.
	 */
	public static final String ICE_CANDIDATE_EXCHANGE_PNAME = CONFIG_PNAME + "ICE_CANDIDATE_EXCHANGE_NAME";
	public static final String ANSWER_EXCHANGE_PNAME = CONFIG_PNAME + "ANSWER_EXCHANGE_NAME";
	
	/**
	 * Rabbit connect string.
	 */
	public static final String CONNECT_STRING = CONFIG_PNAME + "CONNECT_STRING";
}
