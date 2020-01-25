package com.suraj.wmi;

import java.util.List;
import java.util.concurrent.TimeoutException;

public class TestWMI
{
	public static void main(String[] args) throws TimeoutException
	{
		try (WMIConnection wmiConnection = new WMIConnection("localhost"))
		{
			WMIQuery<Win32_Directory> wmiQuery = WMIQuery.builder(Win32_Directory.class)
					.whereClause("Path=''")
					.properties(Win32_Directory.Caption)
					.build();
			
			List<String> directories = wmiConnection.execute(wmiQuery, dataProvider -> dataProvider.getString(Win32_Directory.Caption));
			System.out.println(directories);
		}
	}
	
	public enum Win32_Directory
	{
		Caption, Description, InstallDate, Name, Status, AccessMask, Archive, Compressed, CompressionMethod, CreationClassName, CreationDate,
		CSCreationClassName, CSName, Drive, EightDotThreeFileName, Encrypted, EncryptionMethod, Extension, FileName, FileSize, FileType,
		FSCreationClassName, FSName, Hidden, InUseCount, LastAccessed, LastModified, Path, Readable, System, Writeable
	}
}
