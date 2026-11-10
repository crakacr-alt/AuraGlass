package com.auraglass.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import java.util.List;

/** Read-only media snapshot + real transports exposed by the other app's MediaSession. */
public final class AuraMedia {
    public static final class Track {
        public MediaController controller;
        public String title, artist, application;
        public Bitmap art;
        public boolean playing, canSeek, canSkipNext, canSkipPrev, canToggle;
        public long durationMs, positionMs;
        public String packageName;
    }
    private AuraMedia(){}
    public static Track current(Context context){
        if(!AuraEventListener.granted(context))return null;
        MediaSessionManager manager=(MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if(manager==null)return null;
        try{
            List<MediaController> sessions=manager.getActiveSessions(
                new ComponentName(context,AuraEventListener.class));
            if(sessions==null||sessions.isEmpty())return null;
            MediaController best=null;
            for(MediaController controller:sessions){
                if(controller==null)continue;
                PlaybackState state=controller.getPlaybackState();
                if(state!=null&&state.getState()==PlaybackState.STATE_PLAYING){best=controller;break;}
                if(best==null&&controller.getMetadata()!=null)best=controller;
            }
            if(best==null)return null;
            MediaMetadata meta=best.getMetadata();
            if(meta==null)return null;
            Track track=new Track();
            track.controller=best;
            track.packageName=best.getPackageName();
            track.title=text(meta.getString(MediaMetadata.METADATA_KEY_TITLE),"Без названия");
            track.artist=text(meta.getString(MediaMetadata.METADATA_KEY_ARTIST),"Исполнитель не указан");
            track.art=meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if(track.art==null)track.art=meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
            try{
                PackageManager pm=context.getPackageManager();
                track.application=pm.getApplicationLabel(
                    pm.getApplicationInfo(track.packageName,0)).toString();
            }catch(Exception ignored){track.application=track.packageName;}
            track.durationMs=Math.max(0,meta.getLong(MediaMetadata.METADATA_KEY_DURATION));
            PlaybackState state=best.getPlaybackState();
            if(state!=null){
                track.playing=state.getState()==PlaybackState.STATE_PLAYING;
                long actions=state.getActions();
                track.canSeek=(actions&PlaybackState.ACTION_SEEK_TO)!=0 && track.durationMs>0;
                track.canSkipPrev=(actions&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0;
                track.canSkipNext=(actions&PlaybackState.ACTION_SKIP_TO_NEXT)!=0;
                track.canToggle=(actions&(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|
                    PlaybackState.ACTION_PLAY_PAUSE))!=0;
                long position=state.getPosition();
                if(track.playing&&state.getPlaybackSpeed()!=0){
                    position+=(long)((SystemClock.elapsedRealtime()-state.getLastPositionUpdateTime())*
                        state.getPlaybackSpeed());
                }
                track.positionMs=Math.max(0,track.durationMs>0?
                    Math.min(track.durationMs,position):position);
            }
            return track;
        }catch(SecurityException|IllegalArgumentException ignored){return null;}
    }
    private static String text(String value,String def){
        if(value==null||value.trim().length()==0)return def;
        return value.length()>120?value.substring(0,120):value;
    }
    public static void toggle(Track track){
        if(track==null||!track.canToggle)return;
        try{
            if(track.playing)track.controller.getTransportControls().pause();
            else track.controller.getTransportControls().play();
        }catch(RuntimeException ignored){}
    }
    public static void next(Track track){
        if(track!=null&&track.canSkipNext)try{track.controller.getTransportControls().skipToNext();}
        catch(RuntimeException ignored){}
    }
    public static void previous(Track track){
        if(track!=null&&track.canSkipPrev)try{track.controller.getTransportControls().skipToPrevious();}
        catch(RuntimeException ignored){}
    }
    public static void seek(Track track,long positionMs){
        if(track!=null&&track.canSeek)try{
            track.controller.getTransportControls().seekTo(Math.max(0,Math.min(track.durationMs,positionMs)));
        }catch(RuntimeException ignored){}
    }
    public static boolean openPlayer(Context context,Track track){
        if(track==null)return false;
        try{
            Intent intent=context.getPackageManager().getLaunchIntentForPackage(track.packageName);
            if(intent==null)return false;
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        }catch(RuntimeException ignored){return false;}
    }
}
