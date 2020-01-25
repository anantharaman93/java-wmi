package com.suraj.wmi;

import com.sun.jna.platform.win32.COM.Wbemcli;
import com.sun.jna.platform.win32.COM.WbemcliUtil;
import lombok.*;

@ToString
@Getter
@EqualsAndHashCode
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WMIQuery<T extends Enum<T>>
{
	@NonNull
	@Builder.Default
	private String namespace = WbemcliUtil.DEFAULT_NAMESPACE;
	@Builder.Default
	private int timeout = Wbemcli.WBEM_INFINITE;
	@NonNull
	private String wmiClassName;
	@NonNull
	private T[] properties;
	
	private String whereClause;
	
	public static <T extends Enum<T>> WMIQuery<T> from(Class<T> propertyClass)
	{
		return new WMIQuery<>(WbemcliUtil.DEFAULT_NAMESPACE, Wbemcli.WBEM_INFINITE, propertyClass.getSimpleName(), propertyClass.getEnumConstants(), null);
	}
	
	@SafeVarargs
	public static <T extends Enum<T>> WMIQuery<T> from(Class<T> propertyClass, T... properties)
	{
		return new WMIQuery<>(WbemcliUtil.DEFAULT_NAMESPACE, Wbemcli.WBEM_INFINITE, propertyClass.getSimpleName(), properties, null);
	}
	
	@SafeVarargs
	public static <T extends Enum<T>> WMIQuery<T> from(String className, T... properties)
	{
		return new WMIQuery<>(WbemcliUtil.DEFAULT_NAMESPACE, Wbemcli.WBEM_INFINITE, className, properties, null);
	}
	
	public static <T extends Enum<T>> WMIQuery<T> from(String className, Class<T> propertyClass)
	{
		return new WMIQuery<>(WbemcliUtil.DEFAULT_NAMESPACE, Wbemcli.WBEM_INFINITE, className, propertyClass.getEnumConstants(), null);
	}
	
	public String query()
	{
		StringBuilder sb = new StringBuilder(50);
		sb.append("SELECT ");
		// We earlier checked for at least one enum constant
		sb.append(properties[0].name());
		for (int i = 1; i < properties.length; i++)
		{
			sb.append(',').append(properties[i].name());
		}
		sb.append(" FROM ").append(wmiClassName);
		if (whereClause != null && !whereClause.isEmpty())
		{
			sb.append(" WHERE ").append(whereClause);
		}
		String query = sb.toString().replaceAll("\\\\", "\\\\\\\\");
		return query;
	}
	
	public static <T extends Enum<T>> WMIQueryBuilder<T> builder()
	{
		return new WMIQueryBuilder<T>();
	}
	
	public static <T extends Enum<T>> WMIQueryBuilder<T> builder(Class<T> propertyClass)
	{
		return new WMIQueryBuilder<T>().wmiClassName(propertyClass.getSimpleName()).properties(propertyClass.getEnumConstants());
	}
	
	public static class WMIQueryBuilder<T extends Enum<T>>
	{
		public WMIQueryBuilder<T> properties(@NonNull T... properties)
		{
			this.properties = properties;
			return this;
		}
	}
}
