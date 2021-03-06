/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ws.security.processor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSDocInfo;
import org.apache.ws.security.WSSConfig;
import org.apache.ws.security.WSSecurityEngineResult;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.message.token.Timestamp;
import org.apache.ws.security.util.XmlSchemaDateFormat;
import org.w3c.dom.Element;

import javax.security.auth.callback.CallbackHandler;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Vector;

public class TimestampProcessor implements Processor {
    private static final Log log = LogFactory.getLog(TimestampProcessor.class.getName());

    private WSSConfig wssConfig = null;
    private String tsId;

    private int ttlValue = 0;

    public void setTtlValue(int ttlValue) {
        this.ttlValue = ttlValue;
    }
    
    public void handleToken(
        Element elem, 
        Crypto crypto, 
        Crypto decCrypto, 
        CallbackHandler cb, 
        WSDocInfo wsDocInfo, 
        Vector returnResults, 
        WSSConfig wsc
    ) throws WSSecurityException {
        if (log.isDebugEnabled()) {
            log.debug("Found Timestamp list element");
        }
        wssConfig = wsc;
        //
        // Decode Timestamp, add the found time (created/expiry) to result
        //
        Timestamp timestamp = new Timestamp(elem);
        handleTimestamp(timestamp);
        returnResults.add(
            0,
            new WSSecurityEngineResult(WSConstants.TS, timestamp)
        );
        tsId = elem.getAttributeNS(WSConstants.WSU_NS, "Id");
    }

    public void handleTimestamp(Timestamp timestamp) throws WSSecurityException {
        if (log.isDebugEnabled()) {
            log.debug("Preparing to verify the timestamp");

            DateFormat zulu = new XmlSchemaDateFormat();

            log.debug("Current time: " + zulu.format(Calendar.getInstance().getTime()));
            if (timestamp.getCreated() != null) {
                log.debug("Timestamp created: " + zulu.format(timestamp.getCreated().getTime()));
            }
            if (timestamp.getExpires() != null) {
                log.debug("Timestamp expires: " + zulu.format(timestamp.getExpires().getTime()));
            }
        }

        // Validate whether the security semantics have expired
        Calendar exp = timestamp.getExpires();
        if (exp != null && wssConfig.isTimeStampStrict()) {
            Calendar rightNow = Calendar.getInstance();
            if (exp.before(rightNow)) {
                throw new WSSecurityException(
                    WSSecurityException.MESSAGE_EXPIRED,
                    "invalidTimestamp",
                    new Object[] {"The security semantics of the message have expired"}
                );
            } else {
                // Reject messages with time gap larger than the ttl value.
                if (ttlValue > 0) {
                    Calendar created = timestamp.getCreated();
                    created.add(Calendar.SECOND, ttlValue);
                    if (created.before(exp)) {
                        throw new WSSecurityException(
                                WSSecurityException.MESSAGE_EXPIRED,
                                "invalidTTL",
                                new Object[]{"TTL validation for incoming messages enabled. Invalid TTL " +
                                        "detected between created and expired timestamps"}
                        );
                    }
                }
            }
        }
    }
    
    public String getId() {
        return tsId;
    }    
    
}
