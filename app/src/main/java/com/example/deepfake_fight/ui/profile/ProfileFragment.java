package com.example.deepfake_fight.ui.profile;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.deepfake_fight.R;
import com.example.deepfake_fight.data.AppDatabase;
import com.example.deepfake_fight.data.User;
import com.example.deepfake_fight.databinding.FragmentProfileBinding;
import com.example.deepfake_fight.service.PrefsManager;
import com.example.deepfake_fight.service.SessionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.Executors;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private boolean isRegisterMode = false;
    private String currentCoverPath;   // 封面图缓存路径
    private String currentAvatarPath;  // 头像缓存路径
    private String selectedRegion = ""; // 所在地区

    // 封面图选择器
    private final ActivityResultLauncher<String[]> coverPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && getContext() != null) {
                    requireContext().getContentResolver()
                            .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    saveCoverImage(uri);
                }
            });

    // 头像选择器
    private final ActivityResultLauncher<String[]> avatarPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && getContext() != null) {
                    requireContext().getContentResolver()
                            .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    saveAvatarImage(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SessionManager session = SessionManager.getInstance(requireContext());
        PrefsManager prefs = PrefsManager.getInstance(requireContext());

        // 加载封面图
        currentCoverPath = prefs.getCoverImagePath();
        boolean showGradient = true;
        if (currentCoverPath != null && !currentCoverPath.isEmpty()) {
            File coverFile = new File(currentCoverPath);
            if (coverFile.exists()) {
                Bitmap coverBmp = BitmapFactory.decodeFile(currentCoverPath);
                if (coverBmp != null && binding != null) {
                    binding.ivCover.setImageBitmap(coverBmp);
                    binding.ivCover.setVisibility(View.VISIBLE);
                    binding.vCoverPlaceholder.setVisibility(View.GONE);
                    showGradient = false;
                }
            }
        }
        if (showGradient && binding != null) {
            binding.ivCover.setVisibility(View.GONE);
            binding.vCoverPlaceholder.setVisibility(View.VISIBLE);
        }

        // 加载头像
        currentAvatarPath = prefs.getAvatarPath();
        boolean showDefaultAvatar = true;
        if (currentAvatarPath != null && !currentAvatarPath.isEmpty()) {
            File avatarFile = new File(currentAvatarPath);
            if (avatarFile.exists()) {
                Bitmap avatarBmp = BitmapFactory.decodeFile(currentAvatarPath);
                if (avatarBmp != null && binding != null) {
                    binding.ivAvatar.setImageBitmap(avatarBmp);
                    binding.ivAvatar.setVisibility(View.VISIBLE);
                    binding.layoutAvatarDefault.setVisibility(View.GONE);
                    showDefaultAvatar = false;
                }
            }
        }
        if (showDefaultAvatar && binding != null) {
            binding.ivAvatar.setVisibility(View.GONE);
            binding.layoutAvatarDefault.setVisibility(View.VISIBLE);
        }

        // 登录状态切换
        if (session.isLoggedIn()) {
            showLoggedInUI(session, prefs);
        } else {
            showLoginUI();
        }

        // 更换封面
        binding.btnChangeCover.setOnClickListener(v ->
                coverPicker.launch(new String[]{"image/*"}));

        // 更换头像（点击头像区域）
        binding.ivAvatar.setOnClickListener(v -> {
            if (session.isLoggedIn()) {
                avatarPicker.launch(new String[]{"image/*"});
            }
        });
        binding.layoutAvatarDefault.setOnClickListener(v -> {
            if (session.isLoggedIn()) {
                avatarPicker.launch(new String[]{"image/*"});
            }
        });

        // 编辑资料
        binding.btnEditProfile.setOnClickListener(v -> {
            if (session.isLoggedIn()) {
                showEditProfileDialog(session, prefs);
            }
        });

        // 登录/注册
        binding.btnLogin.setOnClickListener(v -> {
            String username = binding.etUsername.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
                Toast.makeText(getContext(), "请填写用户名和密码", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isRegisterMode) {
                String nickname = binding.etNickname.getText().toString().trim();
                if (TextUtils.isEmpty(nickname)) {
                    Toast.makeText(getContext(), "请填写昵称", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (TextUtils.isEmpty(selectedRegion)) {
                    Toast.makeText(getContext(), "请选择所在地区", Toast.LENGTH_SHORT).show();
                    return;
                }
                register(username, password, nickname);
            } else {
                login(username, password);
            }
        });

        binding.btnRegister.setOnClickListener(v -> {
            isRegisterMode = !isRegisterMode;
            if (isRegisterMode) {
                binding.btnRegister.setText("已有账号？返回登录");
                binding.btnLogin.setText("注 册");
                binding.etNickname.setVisibility(View.VISIBLE);
                binding.tvRegion.setVisibility(View.VISIBLE);
            } else {
                binding.btnRegister.setText("还没有账号？立即注册");
                binding.btnLogin.setText("登 录");
                binding.etNickname.setVisibility(View.GONE);
                binding.tvRegion.setVisibility(View.GONE);
            }
        });

        // 所在地区选择
        binding.tvRegion.setOnClickListener(v -> showRegionPicker());

        binding.btnLogout.setOnClickListener(v -> {
            session.logout();
            showLoginUI();
            Toast.makeText(getContext(), "已退出登录", Toast.LENGTH_SHORT).show();
        });

        // 应用设置入口
        binding.btnSettings.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_settings));
    }

    // ==================== 封面/头像处理 ====================

    private void saveCoverImage(Uri uri) {
        new Thread(() -> {
            try {
                File destFile = new File(requireContext().getCacheDir(),
                        "cover_" + System.currentTimeMillis() + ".jpg");
                try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(destFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }
                currentCoverPath = destFile.getAbsolutePath();
                PrefsManager.getInstance(requireContext()).setCoverImagePath(currentCoverPath);

                requireActivity().runOnUiThread(() -> {
                    Bitmap bmp = BitmapFactory.decodeFile(currentCoverPath);
                    if (bmp != null && binding != null) {
                        binding.ivCover.setImageBitmap(bmp);
                        binding.ivCover.setVisibility(View.VISIBLE);
                        binding.vCoverPlaceholder.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "封面已更新", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "封面设置失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void saveAvatarImage(Uri uri) {
        new Thread(() -> {
            try {
                File destFile = new File(requireContext().getCacheDir(),
                        "avatar_" + System.currentTimeMillis() + ".jpg");
                try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(destFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }
                currentAvatarPath = destFile.getAbsolutePath();
                PrefsManager.getInstance(requireContext()).setAvatarPath(currentAvatarPath);

                requireActivity().runOnUiThread(() -> {
                    Bitmap bmp = BitmapFactory.decodeFile(currentAvatarPath);
                    if (bmp != null && binding != null) {
                        binding.ivAvatar.setImageBitmap(bmp);
                        binding.ivAvatar.setVisibility(View.VISIBLE);
                        binding.layoutAvatarDefault.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "头像已更新", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "头像设置失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ==================== 编辑资料弹窗 ====================

    private void showEditProfileDialog(SessionManager session, PrefsManager prefs) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_edit_profile);

        EditText etNickname = dialog.findViewById(R.id.et_edit_nickname);
        EditText etBio = dialog.findViewById(R.id.et_edit_bio);
        Button btnSave = dialog.findViewById(R.id.btn_save_profile);

        if (etNickname != null) etNickname.setText(session.getNickname());
        if (etBio != null) etBio.setText(prefs.getBio());

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                String newNickname = etNickname != null ? etNickname.getText().toString().trim() : "";
                String newBio = etBio != null ? etBio.getText().toString().trim() : "";

                if (TextUtils.isEmpty(newNickname)) {
                    Toast.makeText(getContext(), "昵称不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 更新 SessionManager
                session.setNickname(newNickname);
                // 更新数据库
                User currentUser = session.getCurrentUser();
                if (currentUser != null) {
                    currentUser.nickname = newNickname;
                    Executors.newSingleThreadExecutor().execute(() -> {
                        AppDatabase.getInstance(requireContext()).userDao().update(currentUser);
                    });
                }
                // 保存简介
                prefs.setBio(newBio);

                // 刷新UI
                if (binding != null) {
                    binding.tvNickname.setText(newNickname);
                    binding.tvBio.setText(newBio.isEmpty() ? "这个人很懒，什么都没写..." : newBio);
                }

                dialog.dismiss();
                Toast.makeText(getContext(), "资料已更新", Toast.LENGTH_SHORT).show();
            });
        }

        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    // ==================== 登录/注册/登出 ====================

    private void showLoginUI() {
        binding.layoutLoginForm.setVisibility(View.VISIBLE);
        binding.layoutMenu.setVisibility(View.GONE);
        binding.layoutEditProfileBtn.setVisibility(View.GONE);
        binding.tvNickname.setText("点击登录");
        binding.tvUsername.setText("登录后享受完整功能");
        binding.tvAvatarLetter.setText("?");
    }

    private void showLoggedInUI(SessionManager session, PrefsManager prefs) {
        binding.layoutLoginForm.setVisibility(View.GONE);
        binding.layoutMenu.setVisibility(View.VISIBLE);
        binding.layoutEditProfileBtn.setVisibility(View.VISIBLE);
        binding.tvNickname.setText(session.getNickname());
        binding.tvUsername.setText("@" + session.getCurrentUser().username);
        binding.tvAvatarLetter.setText(session.getCurrentUser().avatarLetter);
        binding.tvBio.setText(prefs.getBio().isEmpty() ? "这个人很懒，什么都没写..." : prefs.getBio());

        // 加载统计
        AppDatabase.getInstance(requireContext())
                .detectionRecordDao().getRecordCount()
                .observe(getViewLifecycleOwner(), count -> {
                    if (count != null && binding != null) {
                        binding.tvStats.setText(String.valueOf(count));
                    }
                });
    }

    /** 省市两级选择器 */
    private void showRegionPicker() {
        String[] provinces = com.example.deepfake_fight.data.RegionData.getProvinces();
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("选择省份")
                .setItems(provinces, (dialog, which) -> {
                    String province = provinces[which];
                    String[] cities = com.example.deepfake_fight.data.RegionData.getCities(province);
                    if (cities == null || cities.length == 0) return;
                    new android.app.AlertDialog.Builder(requireContext())
                            .setTitle("选择城市 · " + province)
                            .setItems(cities, (dialog2, which2) -> {
                                selectedRegion = province + " · " + cities[which2];
                                binding.tvRegion.setText("所在地区：" + selectedRegion);
                                binding.tvRegion.setTextColor(
                                        getResources().getColor(R.color.text_primary));
                            })
                            .show();
                })
                .show();
    }

    private void login(String username, String password) {
        String hash = SessionManager.hashPassword(password);

        Executors.newSingleThreadExecutor().execute(() -> {
            User user = AppDatabase.getInstance(requireContext())
                    .userDao().login(username, hash);

            requireActivity().runOnUiThread(() -> {
                if (user != null) {
                    SessionManager.getInstance(requireContext()).setCurrentUser(user);
                    showLoggedInUI(SessionManager.getInstance(requireContext()),
                            PrefsManager.getInstance(requireContext()));
                    Toast.makeText(getContext(), "登录成功! 欢迎 " + user.nickname, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "用户名或密码错误", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void register(String username, String password, String nickname) {
        String hash = SessionManager.hashPassword(password);

        Executors.newSingleThreadExecutor().execute(() -> {
            int count = AppDatabase.getInstance(requireContext())
                    .userDao().countByUsername(username);

            if (count > 0) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "用户名已被注册", Toast.LENGTH_SHORT).show());
                return;
            }

            User user = new User(username, hash, nickname, System.currentTimeMillis());
            AppDatabase.getInstance(requireContext()).userDao().insert(user);

            requireActivity().runOnUiThread(() -> {
                SessionManager.getInstance(requireContext()).setCurrentUser(user);
                showLoggedInUI(SessionManager.getInstance(requireContext()),
                        PrefsManager.getInstance(requireContext()));
                Toast.makeText(getContext(), "注册成功! 欢迎 " + nickname, Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
