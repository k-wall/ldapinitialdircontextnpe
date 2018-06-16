# ldapinitialdircontextnpe

Reproduction of a suspected defect in the JVM affect Java's LDAP support [1]. If the LDAP directory server sends an
(unsolicited) notice of disconnection [2], the  client's processing of the this LDAP message can race wth a separate 
thread performing an authentication and lead to a NullPointerException.

Run the reproduction like so:

`mvn  package exec:java`

The program will enter a loop performing a successful LDAP bind against an embedded LDAP server.  The embedded
server has been programmed to send an notice-of-disconnection after the bind response.  After several authentication
iterations (normally several hundred for me), a stack trace will be produced like this.

`java.lang.NullPointerException
	at com.sun.jndi.ldap.LdapClient.authenticate(LdapClient.java:300)
	at com.sun.jndi.ldap.LdapCtx.connect(LdapCtx.java:2791)
	at com.sun.jndi.ldap.LdapCtx.<init>(LdapCtx.java:319)
	at com.sun.jndi.ldap.LdapCtxFactory.getUsingURL(LdapCtxFactory.java:192)
	at com.sun.jndi.ldap.LdapCtxFactory.getUsingURLs(LdapCtxFactory.java:210)
	at com.sun.jndi.ldap.LdapCtxFactory.getLdapCtxInstance(LdapCtxFactory.java:153)
	at com.sun.jndi.ldap.LdapCtxFactory.getInitialContext(LdapCtxFactory.java:83)
	at javax.naming.spi.NamingManager.getInitialContext(NamingManager.java:684)
	at javax.naming.InitialContext.getDefaultInitCtx(InitialContext.java:313)
	at javax.naming.InitialContext.init(InitialContext.java:244)
	at javax.naming.InitialContext.<init>(InitialContext.java:216)
	at javax.naming.directory.InitialDirContext.<init>(InitialDirContext.java:101)
	at LdapConnector.bind(LdapConnector.java:48)
	at Main.main(Main.java:42)
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.lang.reflect.Method.invoke(Method.java:498)
	at org.codehaus.mojo.exec.ExecJavaMojo$1.run(ExecJavaMojo.java:282)
	at java.lang.Thread.run(Thread.java:748)`
	
Analysis: The main thread processing the `new InitialDirContext()` has raced with the background thread processing the
LDAP events form the wire.  This has mutated the state of the LdapClient#conn to null. The main thread then NPEs
at LdapClient.java:300. This is the defect as the InitialDirContext constructor is define as throwing only 
a `NamingException`.
		
[1] https://docs.oracle.com/javase/jndi/tutorial/ldap/security/index.html
[2] https://tools.ietf.org/html/rfc4511, section 4.4.1
