package com.suraj.wmi;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

@Structure.FieldOrder({"User", "UserLength", "Domain", "DomainLength", "Password", "PasswordLength", "Flags"})
public class COAUTHIDENTITY extends Structure
{
	public Pointer User;
	public int UserLength;
	public Pointer Domain;
	public int DomainLength;
	public Pointer Password;
	public int PasswordLength;
	public int Flags;
	
	public static COAUTHIDENTITY newAuth(String username, String domainName, String pass)
	{
		COAUTHIDENTITY auth = new COAUTHIDENTITY();
		auth.User = new Memory(Native.WCHAR_SIZE * (username.length() + 1));
		auth.User.setWideString(0, username);
		auth.UserLength = username.length();
		
		auth.Password = new Memory(Native.WCHAR_SIZE * (pass.length() + 1));
		auth.Password.setWideString(0, pass);
		auth.PasswordLength = pass.length();
		
		domainName = domainName == null ? "" : domainName;
		auth.Domain = new Memory(Native.WCHAR_SIZE * (domainName.length() + 1));
		auth.Domain.setWideString(0, domainName);
		auth.DomainLength = domainName.length();
		
		auth.Flags = 0x2;
		
		return auth;
	}
}
