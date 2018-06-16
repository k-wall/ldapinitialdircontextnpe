/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main
{
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] argv) throws Exception
    {
        int successfulAuthCount = 0;
        try(EmbeddedLdapServer directory = new EmbeddedLdapServer("o=sevenSeas"))
        {
            directory.start();
            directory.applyLdif("test.ldif");

            LdapConnector ldapConnector = new LdapConnector("localhost", directory.getBoundPort());
            String principal = "cn=Horatio Hornblower,ou=people,o=sevenSeas";
            String secret = "secret";

            while(true)
            {
                ldapConnector.bind(principal, secret);
                successfulAuthCount++;
                if (successfulAuthCount % 100 == 0)
                {
                    LOG.info("Successfully LDAP binds {}", successfulAuthCount);
                }
            }
        }
        finally
        {
            LOG.info("Number of successful LDAP binds {}", successfulAuthCount);
        }
    }
}
