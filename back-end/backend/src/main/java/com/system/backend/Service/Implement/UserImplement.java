package com.system.backend.Service.Implement;
import com.system.backend.Dto.User.*;
import com.system.backend.Entity.Token;
import com.system.backend.Entity.TokenType;
import com.system.backend.Entity.User;
import com.system.backend.Repository.TokenRepository;
import com.system.backend.Repository.UserRepository;
import com.system.backend.Service.UserService;
import com.system.backend.util.JwtUtil;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserImplement implements UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private TokenRepository tokenRepository;

    @Override
    public String createToken(UserAuthRequest authRequest) throws Exception  {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getAccount(),
                            authRequest.getPassword())
            );
        } catch (Exception ex) {
            throw new Exception("inavalid account/password");
        }
        // get user ( sure co)
        User user = userRepository.findUserByAccount(authRequest.getAccount());
        System.out.println(user.getUser_id() + user.getAccount());
        //  create Token
        String jwtToken = jwtUtil.generateToken(authRequest.getAccount());
        System.out.println(jwtToken);

        // revoke các token khác của user đã đăng nhập trc đó
        revokeAllUserTokens(user);
        // save token
        saveUserToken(user, jwtToken);
        return jwtToken; // true-token
    }
    @Override
    public String addUser(UserRegisterRequest userRegisterRequest) {
        User userExist = userRepository.findUserByAccount(userRegisterRequest.getAccount());
        if(userExist == null){ // add new
            userExist = User.builder()
                    .role(userRepository.findByRoleId(1))
                    .password(this.passwordEncoder.encode(userRegisterRequest.getPassword()))
                    .email(userRegisterRequest.getEmail())
                    .phone(userRegisterRequest.getPhone())
                    .sex(userRegisterRequest.getSex())
                    .birth(userRegisterRequest.getBirth())
                    .last_name(userRegisterRequest.getLast_name())
                    .first_name(userRegisterRequest.getFirst_name())
                    .account(userRegisterRequest.getAccount())
                    .address(userRegisterRequest.getAddress())
                    .build();
            userRepository.save(userExist);
        } else{
            return "tai khoan da ton tai!";
        }
        return userRepository.findUserByAccount(userExist.getAccount()).getUser_id().toString();

    }
    private void revokeAllUserTokens(User user) {
        List<Token> validUserTokens = tokenRepository.findAllValidTokenByUser(user.getUser_id());
        if (validUserTokens.isEmpty())
            return;
        validUserTokens.forEach(token -> {
            token.setExpired(true);
            token.setRevoked(true);
        });
        tokenRepository.saveAll(validUserTokens);
    }
    private void saveUserToken(User user, String jwtToken) {
        var token = Token.builder()
                .token(jwtToken)
                .tokenType(TokenType.BEARER)
                .expired(false)
                .revoked(false)
                .user(user)
                .build();
        tokenRepository.save(token);
    }

    @Override
    public LoginMessage loginUser(UserAuthRequest loginDTO) {
        String msg = "";
        User user = userRepository.findUserByAccount(loginDTO.getAccount());
        if (user != null) {
            String password = loginDTO.getPassword();
            String encodedPassword = user.getPassword();
            //check pass from user and encoded Password system
            boolean isPwdRight = passwordEncoder.matches(password, encodedPassword);
            if (isPwdRight) {
                Optional<User> employee = userRepository.findUserByAccountAndPassword(loginDTO.getAccount(), encodedPassword);
                if (employee.isPresent()) {
                    return LoginMessage.builder().message("Login Success").status(true).build();
                } else {
                    return LoginMessage.builder().message("Login Fail").status(false).build();
                }
            } else {
                return LoginMessage.builder().message("Password not match").status(false).build();
            }
        }else {
            return LoginMessage.builder().message("Account not exist").status(false).build();
        }
    }

    @Override
    public List<UserResponse> getAllUsers() {
        List<User> list = userRepository.findAll();
        List<UserResponse> listResponse = new ArrayList<>();
        for (User u: list) {
            listResponse.add(convertUserToUserResponse(u));
        }
        return listResponse;
    }

    public UserResponse convertUserToUserResponse(User user){
        return UserResponse.builder()
                .user_id(user.getUser_id())
                .role_id(1)
                .email(user.getEmail())
                .phone(user.getPhone())
                .sex(user.getSex())
                .birth(user.getBirth())
                .last_name(user.getLast_name())
                .first_name(user.getFirst_name())
                .account(user.getAccount())
                .address(user.getAddress())
                .build();
    }
    @Override
    public UserResponse getUser(Integer user_id) {
        User user = userRepository.findByUser_id(user_id);
        return convertUserToUserResponse(user);
    }

    @Override
    public UserResponse getUser(UserAuthRequest userAuthRequest) {
        User user = userRepository.findUserByAccount(userAuthRequest.getAccount());
        return convertUserToUserResponse(user);
    }

    @Override
    public String deleteUser(Integer user_id) {
        String mess = "";
        User user = userRepository.findByUser_id(user_id);
        if(user != null){
            userRepository.delete(user);
            mess = "deleted";
        } else{
            mess = "not exist";
        }
        return mess;
    }

    @Override
    public String updateUser(Integer user_id, UserUpdateRequest userUpdateDTO) {
        String mess = "";
        User user = userRepository.findByUser_id(user_id);
        // nếu như tồn tại cái cũ thì check và update cái mới userUpdateDTO
        if(user != null){
            mess = this.updateUserDetail(userUpdateDTO, user);
        } else{
            mess = "not exist";
        }
        return mess;
    }

    @Override
    public String updateUser(UserUpdateRequest userUpdateDTO) {
        String mess = "";
        User user = userRepository.findUserByAccount(userUpdateDTO.getAccount());
        // nếu như tồn tại cái cũ thì check và update cái mới userUpdateDTO
        if(user != null){
            mess = this.updateUserDetail(userUpdateDTO, user);
        } else{
            mess = "not exist";
        }
        return mess;
    }

    @Override
    public String checkUserPassword(String password, String account) {
        User user = userRepository.findUserByAccount(account);
        String mess ="";
        if(user!= null){
            List<User> list = userRepository.findAll();
            // check pass vs pass cua list
            for (User u: list) {
                if(passwordEncoder.matches(password,u.getPassword())){
                    mess = "password match";
                } else{
                    mess = "password not match";
                }
            }
        } else{
            mess = "Account not exist";
        }
        return mess;
    }

    @Override
    public String setUserPassword(String password, String account) {
        User user = userRepository.findUserByAccount(account);
        String mess ="";
        if(user!= null){
            String encodedPassword = user.getPassword();
            // check có trùng mật khẩu cũ không: khong, thi set
            if(!this.passwordEncoder.matches(password,encodedPassword)){
                user.setPassword(this.passwordEncoder.encode(password));
                userRepository.save(user);
                mess = "Set success!";
            } else{
                mess = "New password duplicate old password!";
            }
        } else{
            mess = "Account not exist";
        }
        return mess;
    }


    public String updateUserDetail(UserUpdateRequest userUpdateDTO, User user) {
        user.setEmail(userUpdateDTO.getEmail());
        user.setAddress(userUpdateDTO.getAddress());
        user.setBirth(userUpdateDTO.getBirth());
        user.setFirst_name(userUpdateDTO.getFirst_name());
        user.setLast_name(userUpdateDTO.getLast_name());
        user.setPhone(userUpdateDTO.getPhone());
        user.setSex(userUpdateDTO.getSex());
        userRepository.save(user);
        return "updated: " + user.getAccount();
    }

}
