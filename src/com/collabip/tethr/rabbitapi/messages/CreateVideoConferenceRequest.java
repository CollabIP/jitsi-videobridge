package com.collabip.tethr.rabbitapi.messages;

import java.util.Date;
import javax.xml.bind.annotation.*;

@XmlRootElement(name="CreateVideoConferenceRequest")
public class CreateVideoConferenceRequest {	
	
	@XmlElement(name="Source")
	public MessageSource Source;
	
	@XmlElement(name="MeetingId")
	public String MeetingId;
	
	@XmlElement(name="TimeStampUtc")
	public String TimeStampUtc;		
}
