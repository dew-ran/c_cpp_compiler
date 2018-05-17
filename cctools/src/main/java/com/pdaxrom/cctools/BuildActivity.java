package com.pdaxrom.cctools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;

import com.jecelyin.common.utils.DLog;
import com.pdaxrom.packagemanager.EnvironmentPath;
import com.pdaxrom.utils.LogItem;
import com.pdaxrom.utils.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class BuildActivity extends Activity {
    private static final String TAG = "BuildActivity";

    private static final String PREFS_NAME = "GCCArgsFile";
    private static final String SYSTEM_SHELL = "/system/bin/sh";

    public static ArrayList<LogItem> errorsList = new ArrayList<LogItem>();
    private TextView mLogView;
    private String mFileName;
    private String mCCToolsDir;
    private String mCommand;
    private Thread mCmdThread;
    private String mWorkDir;
    private String mOutFile;
    private String mTmpDir;
    private String mTmpExeDir;
    private boolean mForceBuild;
    private boolean mForceRun;
    private boolean mRunExe;
    private boolean mBuildNativeActivity;
    private boolean mExecJava;
    private boolean mExecObjC;
    private int mProcessId;
    private int mExitCode;
    private String runme_ca;
    private String runme_na;
    private Handler handler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cctools_build);

        mLogView = findViewById(R.id.buildLog);
        Intent intent = getIntent();

        final String systemShell = "SHELL=" + SYSTEM_SHELL;
        mCmdThread = (Thread) getLastNonConfigurationInstance();
        mFileName = intent.getStringExtra(BuildConstants.EXTRA_FILE_NAME);
        mCCToolsDir = intent.getStringExtra(BuildConstants.EXTRA_CCTOOLS_DIR);
        mWorkDir = new File(mFileName).getParentFile().toString();
        mForceBuild = intent.getBooleanExtra(BuildConstants.EXTRA_FORCE_BUILD, false);
        mForceRun = false;
        mTmpExeDir = EnvironmentPath.getTmpExeDir(this);

        mTmpDir = intent.getStringExtra(BuildConstants.EXTRA_TMP_DIR);
        runme_ca = mTmpDir + "/runme_ca";
        runme_na = mTmpDir + "/runme_na";
        if ((new File(runme_ca)).exists()) {
            (new File(runme_ca)).delete();
        }
        if ((new File(runme_na)).exists()) {
            (new File(runme_na)).delete();
        }
        DLog.w(TAG, "shell " + systemShell);
        DLog.w(TAG, "filename " + mFileName);
        DLog.w(TAG, "cctoolspath " + mCCToolsDir);
        DLog.w(TAG, "workdir " + mWorkDir);
        DLog.w(TAG, "tmpexedir " + mTmpExeDir);

        SharedPreferences mPrefs = getSharedPreferences(CCToolsActivity.SHARED_PREFS_NAME, 0);
        mLogView.setTextSize(Float.valueOf(mPrefs.getString("fontsize", "12")));

        showTitle(getString(R.string.buildwindow_name) + " - " + mFileName);

        String fileName = new File(mFileName).getName();
        if (fileName.contentEquals("Makefile") || fileName.contentEquals("makefile")) {
            mCommand = "make " + systemShell;
            argsDialog(getString(R.string.make_title), getString(R.string.make_args));
            return;

        } else {
            int dotPos = fileName.lastIndexOf(".");
            if (dotPos != -1) {
                mExecJava = false;
                mExecObjC = false;
                String ext = fileName.substring(dotPos);
                mOutFile = fileName.substring(0, dotPos);
                Log.i(TAG, "extension [" + ext + "]");

                if (ext.contentEquals(".mk") || ext.contentEquals(".mak")) {
                    mCommand = "make -f " + fileName + " " + systemShell;
                    argsDialog(getString(R.string.make_title), getString(R.string.make_args));
                    return;

                } else if (ext.contentEquals(".c") || ext.contentEquals(".s") || ext.endsWith(".m")) {
                    mCommand = "gcc " + fileName;
                    if (mForceBuild) {
                        mCommand += " " + mPrefs.getString("force_ccopts", "");
                    }

                } else if (ext.contentEquals(".c++") || ext.contentEquals(".cpp") || ext.endsWith(".mm")) {
                    mCommand = "g++ " + fileName;
                    if (mForceBuild) {
                        mCommand += " " + mPrefs.getString("force_cxxopts", "");
                    }

                } else if ((ext.contentEquals(".f") || ext.contentEquals(".f90") ||
                        ext.contentEquals(".f95") || ext.contentEquals(".f03")) &&
                        new File(mCCToolsDir, "/bin/f77").exists()) {
                    mCommand = "f77 " + fileName;
                    if (mForceBuild) {
                        mCommand += " " + mPrefs.getString("force_ccopts", "");
                    }

                } else if (ext.contentEquals(".java") && (new File(mCCToolsDir, "bin/javac")).exists()) {
                    mCommand = "javac-single " + mOutFile;
                    mExecJava = true;
                }

                if (ext.equals(".m") || ext.equals(".mm")) {
                    mExecObjC = true;
                }
            }
        }

        if (mCommand != null && !mCommand.contentEquals("")) {
            gccDialog(getString(R.string.gcc_title));
            return;
        }
        Log.i(TAG, "Unknown filetype, nothing to do");
        output(getString(R.string.unknown_filetype) + "\n");
        output(getString(R.string.known_filetypes) + "\n");
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return mCmdThread;
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "Finish cmdline thread before activity exit");
        if (mCmdThread != null && mCmdThread.isAlive()) {
            mCmdThread.interrupt();
            Utils.hangupProcessGroup(mProcessId);
        }
        super.onDestroy();
    }

    private void argsDialog(String title, String message) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);

        if (mForceBuild) {
            mCmdThread = new MyThread();
            mCmdThread.start();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setView(input)
                .setPositiveButton(getString(R.string.button_continue), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String cmdargs = input.getText().toString();
                        mCommand += " " + cmdargs;
                        mCmdThread = new MyThread();
                        mCmdThread.start();
                    }
                }).setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                finish();
            }
        }).setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        }).show();
    }

    private void gccDialog(String title) {
        if (mForceBuild) {
            SharedPreferences mPrefs = getSharedPreferences(CCToolsActivity.SHARED_PREFS_NAME, 0);
            mBuildNativeActivity = mPrefs.getBoolean("force_native_activity", false);
            mRunExe = true;
            mForceRun = mPrefs.getBoolean("force_run", true);
            if (mExecJava) {
                // nothing here yet
            } else {
                if (mBuildNativeActivity) {
                    mOutFile = "lib" + mOutFile + ".so";
                    mCommand += " -I" + mCCToolsDir + "/sources/native_app_glue"
                            + " " + mCCToolsDir + "/sources/native_app_glue/android_native_app_glue.c"
                            + " -o " + mOutFile
                            + " -Wl,-soname," + mOutFile + " -shared"
                            + " -Wl,--no-undefined -Wl,-z,noexecstack"
                            + " -llog -landroid -lm";
                } else {
                    mCommand += " -o " + mOutFile;
                }
                if (mExecObjC) {
                    mCommand += " -lobjc";
                }
            }
            mCmdThread = new MyThread();
            mCmdThread.start();
            return;
        }

        final View view = LayoutInflater.from(this).inflate(R.layout.dialog_gcc_args, null);
        final EditText cb_edit = view.findViewById(R.id.gccargs);
        final CheckBox cb_link = view.findViewById(R.id.gccbuildexe);
        final CheckBox cb_native = view.findViewById(R.id.gccnativeactivity);
        final CheckBox cb_run = view.findViewById(R.id.gccrunexe);

        cb_run.setEnabled(false);
        cb_link.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                //cb_native.setEnabled(isChecked);
                cb_run.setEnabled(isChecked);
            }
        });

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        cb_edit.setText(settings.getString("gcc_edit", ""));
        cb_link.setChecked(settings.getBoolean("gcc_link", false));
        cb_native.setChecked(settings.getBoolean("gcc_native", false));
        cb_run.setChecked(settings.getBoolean("gcc_run", false));

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(getString(R.string.button_continue), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        String gccargs = ((EditText) view.findViewById(R.id.gccargs)).getText().toString();
                        boolean buildexe = ((CheckBox) view.findViewById(R.id.gccbuildexe)).isChecked();
                        mBuildNativeActivity = ((CheckBox) view.findViewById(R.id.gccnativeactivity)).isChecked();
                        mRunExe = ((CheckBox) view.findViewById(R.id.gccrunexe)).isChecked();

                        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                        SharedPreferences.Editor editor = settings.edit();

                        if (!gccargs.contentEquals(settings.getString("gcc_edit", ""))) {
                            editor.putString("gcc_edit", gccargs);
                        }
                        if (settings.getBoolean("gcc_run", false) != mRunExe) {
                            editor.putBoolean("gcc_run", mRunExe);
                        }
                        if (settings.getBoolean("gcc_link", false) != buildexe) {
                            editor.putBoolean("gcc_link", buildexe);
                        }
                        if (settings.getBoolean("gcc_native", false) != mBuildNativeActivity) {
                            editor.putBoolean("gcc_native", mBuildNativeActivity);
                        }

                        editor.apply();

                        if (buildexe) {
                            if (mBuildNativeActivity) {
                                mOutFile = "lib" + mOutFile + ".so";
                                mCommand += " -I" + mCCToolsDir + "/sources/native_app_glue"
                                        + " " + mCCToolsDir + "/sources/native_app_glue/android_native_app_glue.c"
                                        + " -o " + mOutFile
                                        + " -Wl,-soname," + mOutFile + " -shared"
                                        + " -Wl,--no-undefined -Wl,-z,noexecstack"
                                        + " -llog -landroid";
                            } else {
                                mCommand += " -o " + mOutFile;
                            }
                            if (mExecObjC) {
                                mCommand += " -lobjc";
                            }
                        } else {
                            if (mBuildNativeActivity) {
                                mCommand += " -I" + mCCToolsDir + "/sources/native_app_glue";
                            }
                            mCommand += " -c";
                            mRunExe = false;
                        }
                        mCommand += " " + gccargs;
                        mCmdThread = new MyThread();
                        mCmdThread.start();
                    }
                }).setNegativeButton(getString(R.string.button_cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                finish();
            }
        }).setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        }).show();
    }

    private void showTitle(final String str) {
        Runnable proc = new Runnable() {
            public void run() {
                setTitle(str);
            }
        };
        handler.post(proc);
    }

    private void output(final String str) {
        Runnable proc = new Runnable() {
            public void run() {
                mLogView.append(str);
            }
        };
        handler.post(proc);
    }

    private void showProgress(final boolean status) {
        Runnable proc = new Runnable() {
            public void run() {
                setProgressBarIndeterminateVisibility(status);
            }
        };
        handler.post(proc);
    }

    @Nullable
    private String checkAndRead(String fName) {
        File f = new File(fName);
        if (!f.exists()) {
            return null;
        }
        try {
            FileInputStream fin = new FileInputStream(fName);
            byte[] buf = new byte[(int) (f.length() - 1)];
            fin.read(buf);
            fin.close();
            f.delete();
            return new String(buf, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        f.delete();
        return null;
    }

    public class MyThread extends Thread {
        @Override
        public void run() {
            try {
                showProgress(true);
                Log.i(TAG, "execute " + mCommand + "\n");
                final String[] envp = {
                        "PWD=" + mWorkDir,
                        "TMPDIR=" + mTmpDir,
                        "PATH=" + mCCToolsDir + "/bin:" + mCCToolsDir + "/sbin:" + System.getenv("PATH"),
                        "ANDROID_ASSETS=/system/app",
                        "ANDROID_BOOTLOGO=1",
                        "ANDROID_DATA=" + mCCToolsDir + "/var/dalvik",
                        "ANDROID_ROOT=/system",
                        "CCTOOLSDIR=" + getCacheDir().getParentFile().getAbsolutePath() + "/root" + "/cctools",
                        "CCTOOLSRES=" + getPackageResourcePath(),
                        "LD_LIBRARY_PATH=" + mCCToolsDir + "/lib:/system/lib:/vendor/lib",
                        "HOME=" + EnvironmentPath.getHomeDir(BuildActivity.this),
                        "TMPEXEDIR=" + mTmpExeDir,
                        "PS1=''"
                };
                String shell = "/system/bin/sh";
                shell = shell.replaceAll("\\s+", " ");
                String[] argv = shell.split("\\s+");
                int[] pId = new int[1];
                FileDescriptor fileDescriptor = Utils.createSubProcess(mWorkDir, argv[0], argv, envp, pId);
                mProcessId = pId[0];
                if (mProcessId > 0) {
                    try {
                        Utils.setPtyUTF8Mode(fileDescriptor, true);
                        Utils.setPtyWindowSize(fileDescriptor, 64, 128, 0, 0);

                        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileDescriptor)));
                        FileOutputStream outputStream = new FileOutputStream(fileDescriptor);
                        Thread execThread = new Thread() {
                            public void run() {
                                DLog.d(TAG, "Waiting for hangup session");
                                mExitCode = Utils.waitFor(mProcessId);
                                DLog.d(TAG, "Subprocess exited: " + mExitCode);
                            }
                        };
                        execThread.start();
                        outputStream.write("export PS1=''\n".getBytes());

                        Pattern pat1 = Pattern.compile("^(\\S+):(\\d+):(\\d+): (\\S+|\\S+ \\S+): (.*)$");
                        Pattern pat2 = Pattern.compile("^(\\S+):(\\d+): (\\S+|\\S+ \\S+): (.*)$");
                        Pattern patClearNewLine = Pattern.compile("(\\x08)\\1+");
                        errorsList.clear();
                        mCommand = "exec " + mCommand + "\n";
                        outputStream.write(mCommand.getBytes());
                        int skipStrings = 3; //skip echos from two command strings
                        do {
                            String errstr;
                            try {
                                errstr = reader.readLine();
                                // remove escape sequence
                                errstr = errstr.replaceAll("\u001b\\[([0-9]|;)*m", "");
                                // remove clearing new line
                                Matcher m = patClearNewLine.matcher(errstr);
                                if (m.find()) {
                                    int length = m.end() - m.start();
                                    if (m.start() > length) {
                                        errstr = errstr.substring(0, m.start() - length) + errstr.substring(m.end());
                                    }
                                }
                            } catch (IOException e) {
                                break;
                            }

                            Matcher m = pat1.matcher(errstr);
                            if (m.find()) {
                                Log.e(TAG, "out " + m.group(1) + "|" + m.group(2) + "|" + m.group(3) + "|" + m.group(4) + "|" + m.group(5));
                                int idx = errorsList.size() - 1;
                                if (idx >= 0 &&
                                        errorsList.get(idx).getFile().contentEquals(m.group(1)) &&
                                        errorsList.get(idx).getType().contentEquals(m.group(4)) &&
                                        errorsList.get(idx).getLine() == Integer.parseInt(m.group(2)) &&
                                        errorsList.get(idx).getPos() == Integer.parseInt(m.group(3))) {
                                    errorsList.get(idx).setMessage(errorsList.get(idx).getMessage() + " " + m.group(5));
                                } else {
                                    LogItem item = new LogItem(m.group(4), m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), m.group(5));
                                    errorsList.add(item);
                                }

                            } else {
                                m = pat2.matcher(errstr);
                                if (m.find()) {
                                    Log.e(TAG, "out " + m.group(1) + "|" + m.group(2) + "|" + m.group(3) + "|" + m.group(4));
                                    int idx = errorsList.size() - 1;
                                    if (idx >= 0 &&
                                            errorsList.get(idx).getFile().contentEquals(m.group(1)) &&
                                            errorsList.get(idx).getType().contentEquals(m.group(3)) &&
                                            errorsList.get(idx).getLine() == Integer.parseInt(m.group(2))) {
                                        errorsList.get(idx).setMessage(errorsList.get(idx).getMessage() + " " + m.group(4));
                                    } else {
                                        LogItem item = new LogItem(m.group(3), m.group(1), Integer.parseInt(m.group(2)), -1, m.group(4));
                                        errorsList.add(item);
                                    }
                                }
                            }
                            if (skipStrings > 0) {
                                skipStrings--;
                            } else {
                                output(errstr + "\n");
                            }
                            Log.i(TAG, errstr);
                        } while (execThread.isAlive());
                        if (mExitCode != 0) {
                            output(getString(R.string.build_error) + " " + mExitCode + "\n");
                            showTitle(getString(R.string.buildwindow_name_error) + " - " + mFileName);
                        } else
                            showTitle(getString(R.string.buildwindow_name_done) + " - " + mFileName);

                        Log.e(TAG, "process exit code " + mExitCode);
                        outputStream.close();
                        reader.close();
                    } catch (IOException ie) {
                        Log.e(TAG, "exception " + ie);
                    }
                }
            } catch (Exception ie) {
                ie.printStackTrace();
                Log.e(TAG, "exec() " + ie);
            }
            output("\n" + getString(R.string.build_done) + "\n\n");
            showProgress(false);

            String runme = checkAndRead(runme_na);
            if (runme != null) {
                mOutFile = (new File(runme)).getName();
                mWorkDir = (new File(runme)).getParent();
                mBuildNativeActivity = true;
                mRunExe = true;
                mExitCode = 0;
            }

            runme = checkAndRead(runme_ca);
            if (runme != null) {
                mOutFile = (new File(runme)).getName();
                mWorkDir = (new File(runme)).getParent();
                mBuildNativeActivity = false;
                mRunExe = true;
                mExitCode = 0;
            }

            String javaClass = "";
            if (mExecJava) {
                javaClass = mOutFile;
                mOutFile = mOutFile + ".jar";
            }

            if (mRunExe && (new File(mWorkDir + "/" + mOutFile).exists()) && mExitCode == 0) {
                if (mBuildNativeActivity) {
                    Intent i = new Intent(BuildActivity.this, LauncherNativeActivity.class);
                    i.putExtra("activity_file", mWorkDir + "/" + mOutFile);
                    startActivity(i);
                } else {
                    Intent i = new Intent(BuildActivity.this, LauncherConsoleActivity.class);
                    if (mExecJava) {
                        i.putExtra(BuildConstants.EXTRA_EXEC_FILE, mCCToolsDir + "/bin/java -cp " + mWorkDir + "/" + mOutFile + " " + javaClass);
                    } else {
                        i.putExtra(BuildConstants.EXTRA_EXEC_FILE, mWorkDir + "/" + mOutFile);
                    }
                    i.putExtra(BuildConstants.EXTRA_CCTOOLS_DIR, mCCToolsDir);
                    i.putExtra(BuildConstants.EXTRA_FORCE_BUILD, mForceRun);
                    startActivity(i);
                }
            }
        }
    }
}
