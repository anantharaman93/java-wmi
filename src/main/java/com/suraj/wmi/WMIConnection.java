package com.suraj.wmi;

import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Wbemcli;
import com.sun.jna.platform.win32.COM.WbemcliUtil;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class WMIConnection implements AutoCloseable
{
	private static final WTypes.BSTR WQL = OleAuto.INSTANCE.SysAllocString("WQL");
	
	static
	{
		initCOM();
		
		Runtime.getRuntime().addShutdownHook(new Thread(WMIConnection::destroy));
	}
	
	public static void destroy()
	{
		unInitCOM();
		OleAuto.INSTANCE.SysFreeString(WQL);
	}
	
	private String machineName;
	private String domainName;
	private String username;
	private String password;
	
	private String usernameWithDomain;
	
	private WTypes.BSTR userNameWithDomainBSTR;
	private WTypes.BSTR passwordBSTR;
	
	private COAUTHIDENTITY AUTH;
	private WTypes.LPOLESTR COLE_DEFAULT_PRINCIPAL;
	
	public WMIConnection(String machineName)
	{
		this(machineName, null, null, null);
	}
	
	public WMIConnection(String machineName, String domainName, String userName, String password)
	{
		this.machineName = machineName;
		this.domainName = domainName == null ? "" : domainName;
		this.username = userName;
		this.password = password;
		this.usernameWithDomain = domainName + "\\" + userName;
		
		initConnection();
	}
	
	private void initConnection()
	{
		if (username == null)
		{
			return;
		}
		
		userNameWithDomainBSTR = OleAuto.INSTANCE.SysAllocString(usernameWithDomain);
		passwordBSTR = OleAuto.INSTANCE.SysAllocString(password);
		
		AUTH = COAUTHIDENTITY.newAuth(username, domainName, password);
		
		Pointer pAuth = new Pointer(-1);
		Pointer.nativeValue(pAuth, -1);
		COLE_DEFAULT_PRINCIPAL = new WTypes.LPOLESTR(pAuth);
	}
	
	public <O, T extends Enum<T>> List<O> execute(WMIQuery<T> wmiQuery, Function<DataProvider<T>, O> dataProvider) throws TimeoutException
	{
		Wbemcli.IWbemServices svc = null;
		Wbemcli.IEnumWbemClassObject enumerator = null;
		
		try
		{
			if (username == null)
			{
				// Connect to the server
				svc = WbemcliUtil.connectServer(wmiQuery.namespace());
				// Send query
				enumerator = selectLocalProperties(svc, wmiQuery);
			}
			else
			{
				// Connect to the server
				svc = connectToRemoveServer(wmiQuery.namespace());
				// Send query
				enumerator = selectRemoteProperties(svc, wmiQuery);
			}
			
			return enumerateProperties(enumerator, wmiQuery, dataProvider);
		}
		finally
		{
			// Cleanup
			if (enumerator != null)
			{
				enumerator.Release();
			}
			if (svc != null)
			{
				svc.Release();
			}
		}
	}
	
	private Wbemcli.IWbemServices connectToRemoveServer(String namespace)
	{
		String networkResource = "\\\\" + machineName + "\\" + namespace;
		WTypes.BSTR networkResourceBSTR = OleAuto.INSTANCE.SysAllocString(networkResource);
		
		try
		{
			// Step 3: ---------------------------------------------------
			// Obtain the initial locator to WMI -------------------------
			Wbemcli.IWbemLocator loc = Wbemcli.IWbemLocator.create();
			
			PointerByReference pSvc = new PointerByReference();
			WinNT.HRESULT hres = loc.ConnectServer(networkResourceBSTR, userNameWithDomainBSTR, passwordBSTR, null, 0, null, null, pSvc);
			// Release the locator. If successful, pSvc contains connection
			// information
			loc.Release();
			if (COMUtils.FAILED(hres))
			{
				throw new COMException(String.format("Could not connect to namespace %s.", networkResource) + " : " + hres.intValue());
			}
			
			hres = Ole32.INSTANCE.CoSetProxyBlanket(pSvc.getValue(), Ole32.RPC_C_AUTHN_DEFAULT, Ole32.RPC_C_AUTHZ_DEFAULT, COLE_DEFAULT_PRINCIPAL, Ole32.RPC_C_AUTHN_LEVEL_PKT_PRIVACY, Ole32.RPC_C_IMP_LEVEL_IMPERSONATE, AUTH, Ole32.EOAC_NONE);
			Wbemcli.IWbemServices svc = new Wbemcli.IWbemServices(pSvc.getValue());
			if (COMUtils.FAILED(hres))
			{
				svc.Release();
				throw new COMException("Could not set proxy blanket: " + hres.intValue());
			}
			
			return svc;
		}
		finally
		{
			OleAuto.INSTANCE.SysFreeString(networkResourceBSTR);
		}
	}
	
	private static <T extends Enum<T>> Wbemcli.IEnumWbemClassObject selectLocalProperties(Wbemcli.IWbemServices svc, WMIQuery<T> wmiQuery)
	{
		// Step 6: --------------------------------------------------
		// Use the IWbemServices pointer to make requests of WMI ----
		String query = wmiQuery.query();
		// Send the query. The flags allow us to return immediately and begin
		// enumerating in the forward direction as results come in.
		return svc.ExecQuery("WQL", query, Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_RETURN_IMMEDIATELY, null);
	}
	
	private <T extends Enum<T>> Wbemcli.IEnumWbemClassObject selectRemoteProperties(Wbemcli.IWbemServices svc, WMIQuery<T> wmiQuery)
	{
		String query = wmiQuery.query();
		PointerByReference pEnumerator = new PointerByReference();
		// Step 6: --------------------------------------------------
		// Use the IWbemServices pointer to make requests of WMI ----
		// Send the query. The flags allow us to return immediately and begin
		// enumerating in the forward direction as results come in.
		WTypes.BSTR queryStr = OleAuto.INSTANCE.SysAllocString(query);
		WinNT.HRESULT hres = svc.ExecQuery(WQL, queryStr, Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_RETURN_IMMEDIATELY, null, pEnumerator);
		OleAuto.INSTANCE.SysFreeString(queryStr);
		if (COMUtils.FAILED(hres))
		{
			svc.Release();
			throw new COMException(String.format("Query '%s' failed.", query) + " : " + hres.intValue());
		}
		
		hres = Ole32.INSTANCE.CoSetProxyBlanket(pEnumerator.getValue(), Ole32.RPC_C_AUTHN_DEFAULT, Ole32.RPC_C_AUTHZ_DEFAULT, COLE_DEFAULT_PRINCIPAL, Ole32.RPC_C_AUTHN_LEVEL_PKT_PRIVACY, Ole32.RPC_C_IMP_LEVEL_IMPERSONATE, AUTH, Ole32.EOAC_NONE);
		if (COMUtils.FAILED(hres))
		{
			throw new COMException("Could not set proxy blanket: " + hres.intValue());
		}
		
		// Step 7:
		// Use the IWbemServices pointer to make requests of WMI ----
		Wbemcli.IEnumWbemClassObject enumerator = new Wbemcli.IEnumWbemClassObject(pEnumerator.getValue());
		return enumerator;
	}
	
	private static final String CLASS_CAST_MSG = "%s is not a %s type. CIM Type is %d and VT type is %d";
	
	private static <O, T extends Enum<T>> List<O> enumerateProperties(Wbemcli.IEnumWbemClassObject enumerator, WMIQuery<T> wmiQuery, Function<DataProvider<T>, O> dataProvider) throws TimeoutException
	{
		// Step 7: -------------------------------------------------
		// Get the data from the query in step 6 -------------------
		Pointer[] pclsObj = new Pointer[1];
		IntByReference uReturn = new IntByReference(0);
		T[] properties = wmiQuery.properties();
		Map<T, WString> wstrMap = new HashMap<>(properties.length);
		WinNT.HRESULT hres;
		for (T property : properties)
		{
			wstrMap.put(property, new WString(property.name()));
		}
		
		List<O> objects = new ArrayList<>();
		
		while (enumerator.getPointer() != Pointer.NULL)
		{
			// Enumerator will be released by calling method so no need to
			// release it here.
			hres = enumerator.Next(wmiQuery.timeout(), pclsObj.length, pclsObj, uReturn);
			// Enumeration complete or no more data; we're done, exit the loop
			if (hres.intValue() == Wbemcli.WBEM_S_FALSE || hres.intValue() == Wbemcli.WBEM_S_NO_MORE_DATA)
			{
				break;
			}
			// Throw exception to notify user of timeout
			if (hres.intValue() == Wbemcli.WBEM_S_TIMEDOUT)
			{
				throw new TimeoutException("No results after " + wmiQuery.timeout() + " ms.");
			}
			// Other exceptions here.
			if (COMUtils.FAILED(hres))
			{
				throw new COMException("Failed to enumerate results.", hres);
			}
			
			Variant.VARIANT.ByReference pVal = new Variant.VARIANT.ByReference();
			IntByReference pType = new IntByReference();
			
			// Get the value of the properties
			Wbemcli.IWbemClassObject clsObj = new Wbemcli.IWbemClassObject(pclsObj[0]);
			
			try
			{
				objects.add(dataProvider.apply(new DataProvider<T>()
				{
					int vtType;
					int cimType;
					T property;
					
					private void prepare(T property)
					{
						clsObj.Get(wstrMap.get(property), 0, pVal, pType, null);
						vtType = (pVal.getValue() == null ? Variant.VT_NULL : pVal.getVarType()).intValue();
						cimType = pType.getValue();
						this.property = property;
					}
					
					@Override
					public String getString(T property)
					{
						prepare(property);
						
						if (cimType == Wbemcli.CIM_STRING)
						{
							return getStr();
						}
						throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "String", cimType, vtType));
					}
					
					@Override
					public String getDateString(T property)
					{
						prepare(property);
						
						if (cimType == Wbemcli.CIM_DATETIME)
						{
							String date = getStr();
							date = date.substring(0, 4) + '-' + date.substring(4, 6) + '-' + date.substring(6, 8);
							return date;
						}
						throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "DateTime", cimType, vtType));
					}
					
					@Override
					public String getDateTimeString(T property)
					{
						prepare(property);
						
						if (cimType == Wbemcli.CIM_DATETIME)
						{
							String date = getStr();
							if (!date.isEmpty())
							{
								date = date.substring(0, 14);
							}
							return date;
						}
						throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "DateTime", cimType, vtType));
					}
					
					@Override
					public String getRefString(T property)
					{
						prepare(property);
						
						if (cimType == Wbemcli.CIM_REFERENCE)
						{
							return getStr();
						}
						throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "Reference", cimType, vtType));
					}
					
					private String getStr()
					{
						Object object = pVal.getValue();
						if (object == null)
						{
							return "";
						}
						else if (vtType == Variant.VT_BSTR)
						{
//							if (object instanceof WTypes.BSTR) {
//								return ((WTypes.BSTR) object).getValue()
//							}
							// This will indirectly call BSTR#getValue
							return object.toString();
						}
						throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "String", cimType, vtType));
					}
					
					@Override
					public long getUint64(T property)
					{
						prepare(property);
						
						Object o = pVal.getValue();
						if (o == null)
						{
							return 0L;
						}
						else if (cimType == Wbemcli.CIM_UINT64 && vtType == Variant.VT_BSTR)
						{
							try
							{
								return Long.parseLong(getStr());
							}
							catch (NumberFormatException e)
							{
								return 0L;
							}
						}
						throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "UINT64", cimType, vtType));
					}
					
					@Override
					public int getUint32(T property)
					{
						prepare(property);
						
						if (cimType == Wbemcli.CIM_UINT32)
						{
							return getInt();
						}
						throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "UINT32", cimType, vtType));
					}
					
					@Override
					public long getUint32asLong(T property)
					{
						prepare(property);
						
						if (cimType == Wbemcli.CIM_UINT32)
						{
							return getInt() & 0xFFFFFFFFL;
						}
						throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "UINT32", cimType, vtType));
					}
					
					@Override
					public int getSint32(T property)
					{
						prepare(property);
						
						if (cimType == Wbemcli.CIM_SINT32)
						{
							return getInt();
						}
						throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "SINT32", cimType, vtType));
					}
					
					@Override
					public int getUint16(T property)
					{
						prepare(property);
						
						if (cimType == Wbemcli.CIM_UINT16)
						{
							return getInt();
						}
						throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "UINT16", cimType, vtType));
					}
					
					private int getInt()
					{
						if (vtType == Variant.VT_I4)
						{
							return pVal.intValue();
						}
						else
						{
							Object object = pVal.getValue();
							if (object == null)
							{
								return 0;
							}
							else
							{
								throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "32-bit integer", cimType, vtType));
							}
						}
					}
					
					@Override
					public float getFloat(T property)
					{
						prepare(property);
						
						if (cimType == Wbemcli.CIM_REAL32 && vtType == Variant.VT_R4)
						{
							return pVal.floatValue();
						}
						else
						{
							Object object = pVal.getValue();
							if (object == null)
							{
								return 0;
							}
							else
							{
								throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "Float", cimType, vtType));
							}
						}
					}
					
					@Override
					public byte getByte(T property)
					{
						prepare(property);
						
						if (vtType == Variant.VT_UI1)
						{
							return pVal.byteValue();
						}
						throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "Byte", cimType, vtType));
					}
					
					@Override
					public short getShort(T property)
					{
						prepare(property);
						
						if (vtType == Variant.VT_I2)
						{
							return pVal.shortValue();
						}
						throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "Short", cimType, vtType));
					}
					
					@Override
					public boolean getBoolean(T property)
					{
						prepare(property);
						
						if (vtType == Variant.VT_BOOL)
						{
							return pVal.booleanValue();
						}
						throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "Boolean", cimType, vtType));
					}
					
					@Override
					public double getDouble(T property)
					{
						prepare(property);
						
						if (vtType == Variant.VT_R8)
						{
							return pVal.doubleValue();
						}
						throw new ClassCastException(String.format(CLASS_CAST_MSG, property.name(), "Double", cimType, vtType));
					}
					
					@Override
					public Object getValue(T property)
					{
						prepare(property);
						
						return pVal.getValue();
					}
				}));
			}
			finally
			{
				clsObj.Release();
			}
		}
		return objects;
	}
	
	@Override
	public void close()
	{
		OleAuto.INSTANCE.SysFreeString(userNameWithDomainBSTR);
		OleAuto.INSTANCE.SysFreeString(passwordBSTR);
	}
	
	// Common
	
	private static boolean securityInitialized = false;
	private static int comThreading = Ole32.COINIT_MULTITHREADED;
	
	private static boolean initCOM()
	{
		boolean comInit;
		// Step 1: --------------------------------------------------
		// Initialize COM. ------------------------------------------
		comInit = initCOM(comThreading);
		if (!comInit)
		{
			comInit = initCOM(switchComThreading());
		}
		// Step 2: --------------------------------------------------
		// Set general COM security levels --------------------------
		if (comInit && !securityInitialized)
		{
			WinNT.HRESULT hres = Ole32.INSTANCE.CoInitializeSecurity(null, -1, null, null, Ole32.RPC_C_AUTHN_LEVEL_DEFAULT, Ole32.RPC_C_IMP_LEVEL_IMPERSONATE, null, Ole32.EOAC_NONE, null);
			// If security already initialized we get RPC_E_TOO_LATE
			// This can be safely ignored
			if (COMUtils.FAILED(hres) && hres.intValue() != WinError.RPC_E_TOO_LATE)
			{
				Ole32.INSTANCE.CoUninitialize();
				throw new COMException("Failed to initialize security.", hres);
			}
			securityInitialized = true;
		}
		return comInit;
	}
	
	private static int switchComThreading()
	{
		if (comThreading == Ole32.COINIT_APARTMENTTHREADED)
		{
			comThreading = Ole32.COINIT_MULTITHREADED;
		}
		else
		{
			comThreading = Ole32.COINIT_APARTMENTTHREADED;
		}
		return comThreading;
	}
	
	private static boolean initCOM(int coInitThreading)
	{
		WinNT.HRESULT hres = Ole32.INSTANCE.CoInitializeEx(null, coInitThreading);
		switch (hres.intValue())
		{
			// Successful local initialization (S_OK) or was already initialized
			// (S_FALSE) but still needs uninit
			case COMUtils.S_OK:
			case COMUtils.S_FALSE:
				return true;
			// COM was already initialized with a different threading model
			case WinError.RPC_E_CHANGED_MODE:
				return false;
			// Any other results is impossible
			default:
				throw new COMException("Failed to initialize COM library.", hres);
		}
	}
	
	private static void unInitCOM()
	{
		Ole32.INSTANCE.CoUninitialize();
	}
}
