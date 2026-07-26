package com.malgeum.geo.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordUpdateRequest(
		@NotBlank(message = "기존 비밀번호는 필수항목입니다.") String originPassword,

		@NotBlank(message = "새 비밀번호는 필수항목입니다.") @Size(min = 8, max = 64, message = "새 비밀번호는 8자 이상이어야 합니다.") String newPassword,
		@NotBlank(message = "비밀번호 확인은 필수항목입니다.") String newPasswordConfirm) {
	@AssertTrue(message = "확인 비밀번호가 일치하지 않습니다.")
	public boolean isNewPasswordConfirmed() {
		return newPassword != null && newPassword.equals(newPasswordConfirm);

	}
}
