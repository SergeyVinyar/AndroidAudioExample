package ru.vinyarsky.androidaudioexample.service;

import android.net.Uri;

import ru.vinyarsky.androidaudioexample.R;

public final class MusicRepository {

    private final Track[] data = {
            new Track("Triangle", "Jason Shaw", R.drawable.image266680, Uri.parse("https://freepd.com/Ballad/Triangle.mp3")),
            new Track("Rubix Cube", "Jason Shaw", R.drawable.image396168, Uri.parse("https://freepd.com/Ballad/Rubix Cube.mp3")),
            new Track("MC Ballad S Early Eighties", "Frank Nora", R.drawable.image533998, Uri.parse("https://freepd.com/Ballad/MC Ballad S Early Eighties.mp3")),
            new Track("Folk Song", "Brian Boyko", R.drawable.image544064, Uri.parse("https://freepd.com/Acoustic/Folk Song.mp3")),
            new Track("Morning Snowflake", "Kevin MacLeod", R.drawable.image208815, Uri.parse("https://freepd.com/Acoustic/Morning Snowflake.mp3")),
    };

    private final int maxIndex = data.length - 1;
    private int currentItemIndex = 0;

    public Track getNext() {
        if (currentItemIndex == maxIndex)
            currentItemIndex = 0;
        else
            currentItemIndex++;
        return getCurrent();
    }

    public Track getPrevious() {
        if (currentItemIndex == 0)
            currentItemIndex = maxIndex;
        else
            currentItemIndex--;
        return getCurrent();
    }

    public Track getCurrent() {
        return data[currentItemIndex];
    }

    public int getTrackCount() {
        return maxIndex + 1;
    }

    public int getCurrentTrackNumber() {
        return currentItemIndex + 1;
    }

    public static class Track {

        private String title;
        private String subtitle;
        private int bitmapResId;
        private Uri uri;

        public Track(String title, String subtitle, int bitmapResId, Uri uri) {
            this.title = title;
            this.subtitle = subtitle;
            this.bitmapResId = bitmapResId;
            this.uri = uri;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public int getBitmapResId() {
            return bitmapResId;
        }

        public Uri getUri() {
            return uri;
        }
    }
}
