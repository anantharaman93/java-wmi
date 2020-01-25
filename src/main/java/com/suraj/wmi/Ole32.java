package com.suraj.wmi;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WTypes.LPOLESTR;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.win32.W32APIOptions;

public interface Ole32 extends com.sun.jna.platform.win32.Ole32
{
	Ole32 INSTANCE = Native.load("Ole32", Ole32.class, W32APIOptions.DEFAULT_OPTIONS);
	
	int RPC_C_AUTHN_LEVEL_PKT_PRIVACY = 0x06;
	
	int RPC_C_AUTHN_DEFAULT = 0xFFFFFFFF;
	int RPC_C_AUTHZ_DEFAULT = 0xffffffff;
	
	HRESULT CoSetProxyBlanket(Pointer pProxy, int dwAuthnSvc, int dwAuthzSvc, LPOLESTR pServerPrincName, int dwAuthnLevel, int dwImpLevel, COAUTHIDENTITY pAuthInfo, int dwCapabilities);
}
