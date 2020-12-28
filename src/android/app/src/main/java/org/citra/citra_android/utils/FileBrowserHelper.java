package org.citra.citra_android.utils;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;

import com.nononsenseapps.filepicker.FilePickerActivity;
import com.nononsenseapps.filepicker.Utils;

import org.citra.citra_android.activities.CustomFilePickerActivity;

import java.io.File;
import java.util.List;

public final class FileBrowserHelper {
    public static void openDirectoryPicker(FragmentActivity activity, int requestCode, int title) {
        Intent i = new Intent(activity, CustomFilePickerActivity.class);

        i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
        i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR);
        i.putExtra(FilePickerActivity.EXTRA_START_PATH,
                Environment.getExternalStorageDirectory().getPath());
        i.putExtra(CustomFilePickerActivity.EXTRA_TITLE, title);

        activity.startActivityForResult(i, requestCode);
    }

    public static void openFilePicker(FragmentActivity activity, int requestCode, int title) {
        Intent i = new Intent(activity, CustomFilePickerActivity.class);

        i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
        i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
        i.putExtra(FilePickerActivity.EXTRA_START_PATH,
                Environment.getExternalStorageDirectory().getPath());
        i.putExtra(CustomFilePickerActivity.EXTRA_TITLE, title);

        activity.startActivityForResult(i, requestCode);
    }

    @Nullable
    public static String getSelectedDirectory(Intent result) {
        // Use the provided utility method to parse the result
        List<Uri> files = Utils.getSelectedFilesFromResult(result);
        if (!files.isEmpty()) {
            File file = Utils.getFileForUri(files.get(0));
            return file.getAbsolutePath();
        }

        return null;
    }
}
