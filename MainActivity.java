package com.webtoapp.template;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    // 🔗 رابط السيرفر الخاص بك على Vercel
    private static final String VERCEL_URL = "https://your-project-name.vercel.app";
    private static final String APP_ID = "app_12345"; // يتم استبداله بمعرّف تطبيقك

    private WebView webView;
    private ProgressBar progressBar;
    
    // عناصر شاشة الحماية والرمز
    private LinearLayout securityLayout;
    private TextView tvSecurityTitle, tvSecurityStatus;
    private EditText etUserInput;
    private Button btnVerify, btnRequestApproval;

    private String targetUrl = "";
    private String authType = "none";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ربط العناصر من الواجهة
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        securityLayout = findViewById(R.id.securityLayout);
        tvSecurityTitle = findViewById(R.id.tvSecurityTitle);
        tvSecurityStatus = findViewById(R.id.tvSecurityStatus);
        etUserInput = findViewById(R.id.etUserInput);
        btnVerify = findViewById(R.id.btnVerify);
        btnRequestApproval = findViewById(R.id.btnRequestApproval);

        setupWebView();

        // جلب البيانات والإعدادات أونلاين من Vercel
        fetchVercelConfig();
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });
    }

    // ==========================================
    // 🌐 الاتصال بسيرفر Vercel أونلاين
    // ==========================================
    private void fetchVercelConfig() {
        new Thread(() -> {
            try {
                URL url = new URL(VERCEL_URL + "/api/apps/config/" + APP_ID);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) builder.append(line);

                    JSONObject json = new JSONObject(builder.toString());
                    targetUrl = json.getString("target_url");
                    boolean isActive = json.optBoolean("is_active", true);

                    JSONObject security = json.getJSONObject("security");
                    authType = security.getString("auth_type");

                    runOnUiThread(() -> {
                        if (!isActive) {
                            showMaintenanceDialog(json.optString("maintenance_message", "التطبيق قيد الصيانة حالياً"));
                            return;
                        }
                        handleSecurityFlow();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "خطأ في الاتصال بالخدمة أونلاين!", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ==========================================
    // 🔒 التحكم بشاشة الدخول
    // ==========================================
    private void handleSecurityFlow() {
        if (authType.equals("none")) {
            // بدون رمز -> فتح الموقع فوراً
            securityLayout.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.loadUrl(targetUrl);
        } else {
            securityLayout.setVisibility(View.VISIBLE);
            webView.setVisibility(View.GONE);

            if (authType.equals("password")) {
                tvSecurityTitle.setText("🔒 أدخل رمز المرور لدخول التطبيق");
                etUserInput.setHint("رمز المرور...");
                btnRequestApproval.setVisibility(View.GONE);
            } else if (authType.equals("admin_approval")) {
                tvSecurityTitle.setText("🛡️ هذا التطبيق يتطلب موافقة أونلاين");
                etUserInput.setHint("بريدك الإلكتروني لطلب الموافقة...");
                btnRequestApproval.setVisibility(View.VISIBLE);
            }

            btnVerify.setOnClickListener(v -> verifyUserAccess(false));
            btnRequestApproval.setOnClickListener(v -> verifyUserAccess(true));
        }
    }

    private void verifyUserAccess(boolean requestApproval) {
        String input = etUserInput.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "يرجى إدخال البيانات المطلوبة!", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                URL url = new URL(VERCEL_URL + "/api/apps/verify-access");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);

                String postData = "app_id=" + APP_ID + "&user_input=" + input + "&request_approval=" + requestApproval;
                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes());
                os.flush();

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) builder.append(line);

                    JSONObject json = new JSONObject(builder.toString());
                    boolean access = json.getBoolean("access");
                    String message = json.getString("message");

                    runOnUiThread(() -> {
                        tvSecurityStatus.setText(message);
                        if (access) {
                            Toast.makeText(MainActivity.this, "تم التحقق بنجاح! 🎉", Toast.LENGTH_SHORT).show();
                            securityLayout.setVisibility(View.GONE);
                            webView.setVisibility(View.VISIBLE);
                            webView.loadUrl(targetUrl);
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "حدث خطأ أثناء الاتصال بالخادم!", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showMaintenanceDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("تنبيه")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("إغلاق", (dialog, which) -> finish())
                .show();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack() && webView.getVisibility() == View.VISIBLE) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
