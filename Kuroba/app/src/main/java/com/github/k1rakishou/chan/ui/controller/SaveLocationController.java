/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.ui.controller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;
import android.view.View;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.activity.StartActivity;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.core.helper.DialogFactory;
import com.github.k1rakishou.chan.core.saver.FileWatcher;
import com.github.k1rakishou.chan.ui.adapter.FilesAdapter;
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper;
import com.github.k1rakishou.chan.ui.layout.FilesLayout;
import com.github.k1rakishou.chan.ui.layout.NewFolderLayout;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton;

import org.jetbrains.annotations.NotNull;

import java.io.File;

import javax.inject.Inject;

import kotlin.Unit;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate;

public class SaveLocationController
        extends Controller
        implements FileWatcher.FileWatcherCallback,
        FilesAdapter.Callback,
        FilesLayout.Callback,
        View.OnClickListener {

    @Inject
    DialogFactory dialogFactory;

    private FilesLayout filesLayout;
    private ColorizableFloatingActionButton setButton;
    private ColorizableFloatingActionButton addButton;
    private RuntimePermissionsHelper runtimePermissionsHelper;
    private FileWatcher fileWatcher;
    private SaveLocationControllerMode mode;
    private SaveLocationControllerCallback callback;

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    public SaveLocationController(
            Context context,
            SaveLocationControllerMode mode,
            SaveLocationControllerCallback callback
    ) {
        super(context);

        this.callback = callback;
        this.mode = mode;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.save_location_screen);

        view = inflate(context, R.layout.controller_save_location);
        filesLayout = view.findViewById(R.id.files_layout);
        filesLayout.setCallback(this);
        setButton = view.findViewById(R.id.set_button);
        setButton.setOnClickListener(this);
        addButton = view.findViewById(R.id.add_button);
        addButton.setOnClickListener(this);

        runtimePermissionsHelper = ((StartActivity) context).getRuntimePermissionsHelper();
        if (runtimePermissionsHelper.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            initialize();
        } else {
            requestPermission();
        }
    }

    @Override
    public void onClick(View v) {
        if (v == setButton) {
            onDirectoryChosen();
            navigationController.popController();
        } else if (v == addButton) {
            @SuppressLint("InflateParams")
            final NewFolderLayout dialogView = (NewFolderLayout) inflate(context, R.layout.layout_folder_add, null);

            DialogFactory.Builder.newBuilder(context, dialogFactory)
                    .withCustomView(dialogView)
                    .withTitle(R.string.save_new_folder)
                    .withPositiveButtonTextId(R.string.add)
                    .withOnPositiveButtonClickListener((dialog) -> {
                        onPositionButtonClick(dialogView, dialog);
                        return Unit.INSTANCE;
                    })
                    .create();
        }
    }

    private void onPositionButtonClick(NewFolderLayout dialogView, DialogInterface dialog) {
        if (!dialogView.getFolderName().matches("\\A\\w+\\z")) {
            showToast("Folder must be a word, no spaces");
        } else {
            File newDir = new File(
                    fileWatcher.getCurrentPath().getAbsolutePath() + File.separator + dialogView.getFolderName());

            if (!newDir.exists() && !newDir.mkdir()) {
                String additionalInfo = "Can write: " + newDir.canWrite() + ", isDirectory: " + newDir.isDirectory();

                throw new IllegalStateException(
                        "Could not create directory: " + newDir.getAbsolutePath() + ", additional info: "
                                + additionalInfo);
            }

            fileWatcher.navigateTo(newDir);

            onDirectoryChosen();
            navigationController.popController();
        }

        dialog.dismiss();
    }

    private void onDirectoryChosen() {
        callback.onDirectorySelected(fileWatcher.getCurrentPath().getAbsolutePath());
    }

    @Override
    public void onFiles(FileWatcher.FileItems fileItems) {
        filesLayout.setFiles(fileItems);
    }

    @Override
    public void onBackClicked() {
        fileWatcher.navigateUp();
    }

    @Override
    public void onFileItemClicked(FileWatcher.FileItem fileItem) {
        if (fileItem.canNavigate()) {
            fileWatcher.navigateTo(fileItem.file);
        }
        // Else ignore, we only do folder selection here
    }

    private void requestPermission() {
        runtimePermissionsHelper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, granted -> {
            if (runtimePermissionsHelper.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                initialize();
            } else {
                runtimePermissionsHelper.showPermissionRequiredDialog(
                        context,
                        getString(R.string.save_location_storage_permission_required_title),
                        getString(R.string.save_location_storage_permission_required),
                        this::requestPermission
                );
            }
        });
    }

    private void initialize() {
        fileWatcher = new FileWatcher(this, getInitialLocation());
        filesLayout.initialize();
        fileWatcher.initialize();
    }

    private File getInitialLocation() {
        if (mode == SaveLocationControllerMode.ImageSaveLocation) {
            if (ChanSettings.saveLocation.isFileDirActive()) {
                if (ChanSettings.saveLocation.getFileApiBaseDir().get().isEmpty()) {
                    return getExternalStorageDir();
                }

                return new File(ChanSettings.saveLocation.getFileApiBaseDir().get());
            }
        }

        return getExternalStorageDir();
    }

    private File getExternalStorageDir() {
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        if (!externalStorageDirectory.exists()) {
            throw new IllegalStateException(
                    "External storage dir does not exist! " + "State = " + Environment.getExternalStorageState());
        }

        return externalStorageDirectory;
    }

    public interface SaveLocationControllerCallback {
        void onDirectorySelected(String dirPath);
    }

    public enum SaveLocationControllerMode {
        ImageSaveLocation
    }
}
