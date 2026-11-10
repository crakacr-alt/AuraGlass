package com.auraglass.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Read-only one-file bridge to Android's system APK installer. No arbitrary paths. */
public final class UpdateApkProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File checked(Uri uri)throws FileNotFoundException{
        if(uri==null||uri.getPathSegments().size()!=1)
            throw new FileNotFoundException("Invalid APK path");
        String name=uri.getLastPathSegment();
        if(name==null||!name.matches("AuraGlass-update-v?[0-9]+\\.[0-9]+\\.[0-9]+\\.apk"))
            throw new FileNotFoundException("Unsupported APK filename");
        if(!name.equals(Config.str(getContext(),"update_download_file","")))
            throw new FileNotFoundException("Not current AURA update");
        File root=getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if(root==null)throw new FileNotFoundException("Storage missing");
        File result=new File(root,name);
        if(!result.exists()||!result.isFile())throw new FileNotFoundException("APK missing");
        return result;
    }
    @Override public String getType(Uri uri){return "application/vnd.android.package-archive";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode))throw new FileNotFoundException("Read-only");
        return ParcelFileDescriptor.open(checked(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{
            File file=checked(uri);
            MatrixCursor cursor=new MatrixCursor(new String[]{
                OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});
            cursor.addRow(new Object[]{file.getName(),file.length()});
            return cursor;
        }catch(FileNotFoundException e){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){return 0;}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){return 0;}
}
