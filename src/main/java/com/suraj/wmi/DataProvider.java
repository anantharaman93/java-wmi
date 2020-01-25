package com.suraj.wmi;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

public interface DataProvider<T>
{
	String getString(T property);
	
	String getDateTimeString(T property);
	
	default LocalDateTime getLocalDateTime(T property)
	{
		String string = getDateTimeString(property);
		if (string.isEmpty())
		{
			return LocalDateTime.MIN;
		}
		int year = Integer.parseInt(string.substring(0, 4));
		int month = Integer.parseInt(string.substring(4, 6));
		int date = Integer.parseInt(string.substring(6, 8));
		int hour = Integer.parseInt(string.substring(8, 10));
		int minute = Integer.parseInt(string.substring(10, 12));
		int second = Integer.parseInt(string.substring(12, 14));
		
		LocalDateTime localDateTime = LocalDateTime.of(year, month, date, hour, minute, second);
		return localDateTime;
	}
	
	String getDateString(T property);
	
	String getRefString(T property);
	
	long getUint64(T property);
	
	int getUint32(T property);
	
	long getUint32asLong(T property);
	
	int getSint32(T property);
	
	int getUint16(T property);
	
	float getFloat(T property);
	
	// Not Given
	byte getByte(T property);
	
	short getShort(T property);
	
	boolean getBoolean(T property);
	
	double getDouble(T property);
	
	Object getValue(T property);
	
	static <T extends Enum<T>> Function<DataProvider<T>, List<Object>> asList(Class<T> propertyClass)
	{
		T[] properties = propertyClass.getEnumConstants();
		return dataProvider -> {
			List<Object> values = new ArrayList<>(properties.length);
			for (T property : properties)
			{
				values.add(dataProvider.getValue(property));
			}
			return Collections.unmodifiableList(values);
		};
	}
	
	static <T extends Enum<T>> Function<DataProvider<T>, Map<T, Object>> asMap(Class<T> propertyClass)
	{
		T[] properties = propertyClass.getEnumConstants();
		return dataProvider -> {
			Map<T, Object> values = new HashMap<>(properties.length);
			for (T property : properties)
			{
				values.put(property, dataProvider.getValue(property));
			}
			return Collections.unmodifiableMap(values);
		};
	}
}