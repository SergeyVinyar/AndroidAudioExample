package ru.vinyarsky.androidaudioexample.service;

import android.net.Uri;

import ru.vinyarsky.androidaudioexample.R;

final class MusicRepository {

    private final Track[] data = {
            new Track("Triangle", "Jason Shaw", R.drawable.image266680, Uri.parse("https://freepd.com/Ballad/Triangle.mp3"), (3 * 60 + 41) * 1000),
            new Track("Rubix Cube", "Jason Shaw", R.drawable.image396168, Uri.parse("https://freepd.com/Ballad/Rubix Cube.mp3"), (3 * 60 + 44) * 1000),
            new Track("MC Ballad S Early Eighties", "Frank Nora", R.drawable.image533998, Uri.parse("https://freepd.com/Ballad/MC Ballad S Early Eighties.mp3"), (2 * 60 + 50) * 1000),
            new Track("Folk Song", "Brian Boyko", R.drawable.image544064, Uri.parse("https://freepd.com/Acoustic/Folk Song.mp3"), (3 * 60 + 5) * 1000),
            new Track("Morning Snowflake", "Kevin MacLeod", R.drawable.image208815, Uri.parse("https://freepd.com/Acoustic/Morning Snowflake.mp3"), (2 * 60 + 0) * 1000),
    };

    private final int maxIndex = data.length - 1;
    private int currentItemIndex = 0;

    Track getNext() {
        if (currentItemIndex == maxIndex)
            currentItemIndex = 0;
        else
            currentItemIndex++;
        return getCurrent();
    }

    Track getPrevious() {
        if (currentItemIndex == 0)
            currentItemIndex = maxIndex;
        else
            currentItemIndex--;
        return getCurrent();
    }

    Track getCurrent() {
        return data[currentItemIndex];
    }

    static class Track {

        private String title;
        private String artist;
        private int bitmapResId;
        private Uri uri;
        private long duration; // in ms

        Track(String title, String artist, int bitmapResId, Uri uri, long duration) {
            this.title = title;
            this.artist = artist;
            this.bitmapResId = bitmapResId;
            this.uri = uri;
            this.duration = duration;
        }

        String getTitle() {
            return title;
        }

        String getArtist() {
            return artist;
        }

        int getBitmapResId() {
            return bitmapResId;
        }

        Uri getUri() {
            return uri;
        }

        long getDuration() {
            return duration;
        }
    }
}
