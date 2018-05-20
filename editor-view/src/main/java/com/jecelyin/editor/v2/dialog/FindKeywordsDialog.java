/*
 * Copyright 2018 Mr Duy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jecelyin.editor.v2.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;

import com.duy.ide.editor.editor.R;
import com.jecelyin.common.widget.DrawClickableEditText;
import com.jecelyin.editor.v2.utils.DBHelper;

import java.util.List;

/**
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */

public class FindKeywordsDialog extends AbstractDialog {
    private final boolean isReplace;
    private final DrawClickableEditText editText;

    public FindKeywordsDialog(Context context, DrawClickableEditText editText, boolean isReplace) {
        super(context);
        this.isReplace = isReplace;
        this.editText = editText;
    }

    @Override
    public void show() {
        final List<String> items = DBHelper.getInstance(context).getFindKeywords(isReplace);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setNegativeButton(R.string.clear_history, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                DBHelper.getInstance(context).clearFindKeywords(isReplace);
                dialog.cancel();
            }
        });
        builder.setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.setTitle(isReplace ? R.string.replace_log : R.string.find_log);
        builder.setItems(items.toArray(new String[1]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                editText.setText(items.get(which));
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        handleDialog(dialog);
    }
}
