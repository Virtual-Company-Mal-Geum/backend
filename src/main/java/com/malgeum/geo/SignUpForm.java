package com.malgeum.geo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;

@Getter
@Data
public class SignUpForm {
    @NotEmpty(message = "성함은 필수항목입니다.")
    private String name;

    private String company;

    @NotEmpty(message = "전화번호는 필수항목입니다.")
    private String phone;
    @NotEmpty(message = "사용자ID는 필수항목입니다.")
    @Size(min = 3, max = 25)
    private String registerId;
    @NotEmpty(message = "비밀번호는 필수항목입니다.")
    private String password1;
    @NotEmpty(message = "비밀번호 확인은 필수항목입니다.")
    private String password2;
    @NotEmpty(message="이메일은 필수항목입니다.")
	@Email
    private String email;
}
