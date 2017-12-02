package com.dhpcs.liquidity.activity

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.widget.LinearLayout
import android.widget.TextView
import com.dhpcs.liquidity.R
import de.psdev.licensesdialog.NoticesXmlParser

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        setSupportActionBar(toolbar)
        val actionBar = supportActionBar!!
        actionBar.setDisplayHomeAsUpEnabled(true)

        val linearLayoutLicences = findViewById<LinearLayout>(R.id.linearlayout_licences)

        val notices = NoticesXmlParser.parse(resources.openRawResource(R.raw.licences)).notices

        for (notice in notices) {
            val linearLayoutAcknowledgement = layoutInflater.inflate(
                    R.layout.linearlayout_acknowledgement,
                    linearLayoutLicences,
                    false
            ) as LinearLayout

            val textViewName = linearLayoutAcknowledgement
                    .findViewById<TextView>(R.id.textview_name)
            val textViewUrl = linearLayoutAcknowledgement
                    .findViewById<TextView>(R.id.textview_url)
            val textViewCopyrightAndLicense = linearLayoutAcknowledgement
                    .findViewById<TextView>(R.id.textview_copyright_and_license)

            textViewName.text = notice.name

            val url = notice.url
            if (url == null) textViewUrl.visibility = TextView.GONE else textViewUrl.text = url

            val copyright = notice.copyright
            val licenseSummary = notice.license.getSummaryText(this)
            textViewCopyrightAndLicense.text = if (copyright == null) {
                licenseSummary
            } else {
                getString(
                        R.string.acknowledgement_copyright_and_license_format_string,
                        copyright,
                        licenseSummary
                )
            }

            linearLayoutLicences!!.addView(linearLayoutAcknowledgement)
        }
    }

}
