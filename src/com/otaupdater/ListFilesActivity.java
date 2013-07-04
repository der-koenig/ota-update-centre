/*
 * Copyright (C) 2012 OTA Update Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may only use this file in compliance with the license and provided you are not associated with or are in co-operation anyone by the name 'X Vanderpoel'.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.otaupdater;

import java.io.DataOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.OpenRecoverySystem;
import android.os.OpenRecoverySystem.RecoveryAction;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

public class ListFilesActivity extends ListActivity implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {
    private ArrayAdapter<String> fileListAdapter;
    private ArrayList<String> fileList = new ArrayList<String>();
    private ArrayList<String> pathList = new ArrayList<String>();

    public static final int DL_PATH_LEN = Config.DL_PATH.length();

    private void listFiles(File dir) {
        fileList.clear();
        pathList.clear();
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            fileList.add(file.getPath().substring(DL_PATH_LEN));
            pathList.add(file.getPath());
        }
        fileListAdapter.notifyDataSetChanged();
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Config.DL_PATH_FILE.mkdirs(); //just in case
        String extState = Environment.getExternalStorageState();
        if ((!extState.equals(Environment.MEDIA_MOUNTED) && !extState.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) || !Config.DL_PATH_FILE.exists()) {
            Toast.makeText(this, extState.equals(Environment.MEDIA_SHARED) ? R.string.toast_nosd_shared : R.string.toast_nosd_error, Toast.LENGTH_LONG).show();
            finish();
        }

        fileListAdapter = new ArrayAdapter<String>(this, R.layout.row, R.id.filename, fileList);
        setListAdapter(fileListAdapter);
        listFiles(Config.DL_PATH_FILE);

        this.getListView().setOnItemClickListener(this);
        this.getListView().setOnItemLongClickListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.prune:
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle(R.string.alert_prune_title);
            alert.setCancelable(true);
            alert.setItems(R.array.file_ages, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    long maxAge = 2592000000l; //1 month
                    switch (which) {
                    case 0:
                        maxAge = 604800000l; //1 week
                        break;
                    case 1:
                        maxAge = 1209600000l; //2 weeks
                        break;
                    case 2:
                        maxAge = 2592000000l; //1 month
                        break;
                    case 3:
                        maxAge = 7776000000l; //3 months
                        break;
                    case 4:
                        maxAge = 15552000000l; //6 months
                        break;
                    }
                    pruneFiles(maxAge);
                    listFiles(Config.DL_PATH_FILE);
                }
            });

            alert.create().show();
            break;
        case R.id.list_refresh:
            listFiles(Config.DL_PATH_FILE);
            break;
        }
        return true;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
        processItem(pos);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
        processItem(pos);
        return true;
    }

    private void processItem(final int pos) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.alert_options_title);
        builder.setItems(R.array.file_actions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                String path = pathList.get(pos);
                final File file = new File(path);

                AlertDialog.Builder alert;
                switch (which) {
                case 0:
                    installFileDialog(ListFilesActivity.this, file);
                    break;
                case 1:
                    alert = new AlertDialog.Builder(ListFilesActivity.this);
                    alert.setTitle(R.string.alert_rename_title);
                    alert.setMessage(R.string.alert_rename_message);

                    final EditText input = new EditText(ListFilesActivity.this);
                    alert.setView(input);
                    alert.setPositiveButton(R.string.alert_rename, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            dialog.dismiss();

                            String newName = input.getText().toString();
                            if (!newName.endsWith(".zip")) newName += ".zip";
                            File newFile = new File(Config.DL_PATH_FILE, newName);
                            boolean renamed = file.renameTo(newFile);

                            if (renamed) {
                                Toast.makeText(getApplicationContext(), R.string.toast_rename, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getApplicationContext(), R.string.toast_rename_error, Toast.LENGTH_SHORT).show();
                            }

                            listFiles(Config.DL_PATH_FILE);
                            return;
                        }
                    });
                    alert.setNegativeButton(R.string.alert_cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    alert.create().show();
                    break;
                case 2:
                    boolean deleted = file.delete();

                    if (deleted) {
                        Toast.makeText(getApplicationContext(), R.string.toast_delete, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(), R.string.toast_delete_error, Toast.LENGTH_SHORT).show();
                    }

                    listFiles(Config.DL_PATH_FILE);
                    break;
                case 3:
                    VerificationTask verificationTask = new VerificationTask(file, ListFilesActivity.this);
                    verificationTask.execute();
                    break;
                }
            }
        });

        builder.setCancelable(true);
        builder.create().show();
    }

    protected static void installFileDialog(final Context ctx, final File file) {
        Resources r = ctx.getResources();
        String[] installOpts = r.getStringArray(R.array.install_options);
        final boolean[] selectedOpts = new boolean[installOpts.length];

        AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
        alert.setTitle(R.string.alert_install_title);
//        alert.setMessage(R.string.alert_install_message);
        if (Utils.getNoflash()) { //can't flash programmatically, must flash manually
            alert.setMessage(ctx.getString(R.string.alert_noinstall_message, file.getAbsolutePath()));
            alert.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        } else {
            alert.setMultiChoiceItems(installOpts, selectedOpts, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                    selectedOpts[which] = isChecked;
                }
            });
            alert.setPositiveButton(R.string.alert_install, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();

                    AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
                    alert.setTitle(R.string.alert_install_title);
                    alert.setMessage(R.string.alert_install_message);
                    alert.setPositiveButton(R.string.alert_install, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                String name = file.getName();

                                OpenRecoverySystem.clearQueuedActions();

                                if (selectedOpts[0]) {
                                    OpenRecoverySystem.queueAction(RecoveryAction.backup());
                                }
                                if (selectedOpts[1]) {
                                    OpenRecoverySystem.queueAction(RecoveryAction.wipe(
                                            EnumSet.of(RecoveryAction.WipeTarget.Data)));
                                }
                                if (selectedOpts[2]) {
                                    OpenRecoverySystem.queueAction(RecoveryAction.wipe(
                                            EnumSet.of(RecoveryAction.WipeTarget.Cache)));
                                }

                                OpenRecoverySystem.queueAction(RecoveryAction.installPackage(
                                        "/" + Utils.getRcvrySdPath() + "/OTA-Updater/download/" + name));

                                ((PowerManager) ctx.getSystemService(POWER_SERVICE)).reboot("recovery");
                            } catch (Exception e2) {
                                e2.printStackTrace();
                            }
                        }
                    });
                    alert.setNegativeButton(R.string.alert_cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    alert.create().show();
                }
            });
            alert.setNegativeButton(R.string.alert_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        }
        alert.create().show();
    }

    private void pruneFiles(long maxAge) {
        File dir = new File(Config.DL_PATH);
        File[] files = dir.listFiles();

        boolean success = true;
        for (File f : files) {
            final Long lastmodified = f.lastModified();
            if (lastmodified + maxAge < System.currentTimeMillis()) {
                if (!f.delete()) success = false;
            }
        }

        if (success) {
            Toast.makeText(getApplicationContext(), R.string.toast_prune, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), R.string.toast_prune_error, Toast.LENGTH_SHORT).show();
        }
    }

    private class VerificationTask extends AsyncTask<File, Integer, Boolean> implements OpenRecoverySystem.ProgressListener {
        private ProgressDialog dialog = null;

        private Context ctx = null;
        private final WakeLock wl;

        private File packageFile;

        private boolean done = false;
        private String errorMessage = null;

        public VerificationTask(File packageFile, ListActivity activity) {
            this.ctx = activity;
            this.packageFile = packageFile;

            dialog = new ProgressDialog(ctx);

            dialog.setTitle("Verifying package");
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setCancelable(false);
            dialog.setProgress(0);
            dialog.setProgressNumberFormat(null);
            dialog.setButton(Dialog.BUTTON_NEGATIVE, ctx.getString(R.string.alert_cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    VerificationTask.this.cancel(true);
                }
            });

            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, OTAUpdaterActivity.class.getName());
        }

        @Override
        public void onProgress(int progress) {
            dialog.setProgress(progress);
        }

        @Override
        protected void onPreExecute() {
            done = false;
            dialog.show();
            wl.acquire();
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }

            done = true;
            wl.release();
            wl.acquire(Config.WAKE_TIMEOUT);

            if (success) {
                Toast.makeText(ctx, "Verification succeeded", Toast.LENGTH_LONG).show();
            } else {
                if (errorMessage != null) {
                    Toast.makeText(ctx, "Verification failed: " + errorMessage, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(ctx, "Verification failed", Toast.LENGTH_LONG).show();
                }
            }
        }
        @Override
        protected void onCancelled(Boolean success) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }

            done = true;
            wl.release();
            wl.acquire(Config.WAKE_TIMEOUT);

            if (success) {
                Toast.makeText(ctx, "Verification succeeded", Toast.LENGTH_LONG).show();
            } else {
                if (errorMessage != null) {
                    Toast.makeText(ctx, "Verification failed: " + errorMessage, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(ctx, "Verification failed", Toast.LENGTH_LONG).show();
                }
            }
        }

        private class VerificationResult {
            public boolean result = false;
            public Throwable exception = null;
        }

        @Override
        protected Boolean doInBackground(final File... params) {
            final VerificationResult result = new VerificationResult();
            try {
                Thread verificationThread = new Thread(new Runnable() {
                    public void run() {
                        try {
                            OpenRecoverySystem.verifyPackage(packageFile, VerificationTask.this, null);
                            result.result = true;
                        } catch (Exception e) {
                            result.result = false;
                            result.exception = e;
                        }
                    }
                });
                verificationThread.start();
                while(verificationThread.isAlive()) {
                    try {
                        if (isCancelled())
                            verificationThread.interrupt();
                        Thread.sleep(100);
                    } catch (Exception e1) {
                        result.result = false;
                        result.exception = e1;
                    }
                }
                if (result.exception != null)
                    errorMessage = result.exception.getMessage();
                return result.result;
            } catch (Exception e) {
                Log.e("OTAUpdater", "Error verifying package", e);
                errorMessage = e.getMessage();
                return false;
            }
        }
    }
}
