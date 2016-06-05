package com.dhpcs.liquidity.activity;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dhpcs.liquidity.R;

import java.util.List;

import de.psdev.licensesdialog.NoticesXmlParser;
import de.psdev.licensesdialog.model.Notice;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayHomeAsUpEnabled(true);

        LinearLayout linearLayoutLicences = (LinearLayout) findViewById(R.id.linearlayout_licences);

        try {

            List<Notice> notices = NoticesXmlParser.parse(
                    getResources().openRawResource(R.raw.licences)
            ).getNotices();

            for (Notice notice : notices) {

                LinearLayout linearLayoutAcknowledgement = (LinearLayout)
                        getLayoutInflater().inflate(
                                R.layout.linearlayout_acknowledgement,
                                linearLayoutLicences,
                                false
                        );

                TextView textViewName = (TextView) linearLayoutAcknowledgement
                        .findViewById(R.id.textview_name);
                TextView textViewUrl = (TextView) linearLayoutAcknowledgement
                        .findViewById(R.id.textview_url);
                TextView textViewCopyrightAndLicense = (TextView) linearLayoutAcknowledgement
                        .findViewById(R.id.textview_copyright_and_license);

                textViewName.setText(notice.getName());

                String url = notice.getUrl();
                if (url == null) {
                    textViewUrl.setVisibility(TextView.GONE);
                } else {
                    textViewUrl.setText(url);
                }

                String copyright = notice.getCopyright();
                String licenseSummary = notice.getLicense().getSummaryText(this);
                if (copyright == null) {
                    textViewCopyrightAndLicense.setText(licenseSummary);
                } else {
                    textViewCopyrightAndLicense.setText(
                            getString(
                                    R.string.acknowledgement_copyright_and_license_format_string,
                                    copyright,
                                    licenseSummary
                            )
                    );
                }

                assert linearLayoutLicences != null;
                linearLayoutLicences.addView(linearLayoutAcknowledgement);

            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
