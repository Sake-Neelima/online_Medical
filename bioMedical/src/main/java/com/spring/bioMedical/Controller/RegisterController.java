package com.spring.bioMedical.Controller;

import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.nulabinc.zxcvbn.Strength;
import com.nulabinc.zxcvbn.Zxcvbn;
import com.spring.bioMedical.entity.User;
import com.spring.bioMedical.service.EmailService;
import com.spring.bioMedical.service.UserService;

@Controller
public class RegisterController {

    private UserService userService;
    private EmailService emailService;

    @Autowired
    public RegisterController(UserService userService, EmailService emailService) {
        this.userService = userService;
        this.emailService = emailService;
    }

    // Return registration form template
    @RequestMapping(value="/register", method = RequestMethod.GET)
    public ModelAndView showRegistrationPage(ModelAndView modelAndView, User user){
        modelAndView.addObject("user", user);
        modelAndView.setViewName("register");
        return modelAndView;
    }

    // Process form input data
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    public ModelAndView processRegistrationForm(ModelAndView modelAndView, @Valid User user, BindingResult bindingResult, HttpServletRequest request) {
        // Lookup user in database by e-mail
        User userExists = userService.findByEmail(user.getEmail());

        if (userExists != null) {
            modelAndView.addObject("alreadyRegisteredMessage", "Oops! There is already a user registered with the email provided.");
            modelAndView.setViewName("register");
            bindingResult.reject("email");
        }

        if (bindingResult.hasErrors()) { 
            modelAndView.setViewName("register");        
        } else { 
            // new user so we create user and send confirmation e-mail
            user.setEnabled(false);
            user.setRole("ROLE_USER");

            // Generate random 36-character string token for confirmation link
            user.setConfirmationToken(UUID.randomUUID().toString());
            userService.saveUser(user);

            // Determine the application URL
            String appUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
            String confirmationUrl = appUrl + "/confirm?token=" + user.getConfirmationToken();

            SimpleMailMessage registrationEmail = new SimpleMailMessage();
            registrationEmail.setTo(user.getEmail());
            registrationEmail.setSubject("Registration Confirmation");
            registrationEmail.setText("To confirm your e-mail address, please click the link below:\n" + confirmationUrl);
            registrationEmail.setFrom("spring.email.auth@gmail.com");

            emailService.sendEmail(registrationEmail);

            modelAndView.addObject("confirmationMessage", "A confirmation e-mail has been sent to " + user.getEmail());
            modelAndView.setViewName("register");
        }

        return modelAndView;
    }

    // Process confirmation link (GET)
    @RequestMapping(value="/confirm", method = RequestMethod.GET)
    public ModelAndView confirmRegistration(ModelAndView modelAndView, @RequestParam("token") String token) {
        User user = userService.findByConfirmationToken(token);

        if (user == null) { 
            modelAndView.addObject("invalidToken", "Oops! This is an invalid confirmation link.");
            modelAndView.setViewName("error");
        } else { 
            modelAndView.addObject("confirmationToken", user.getConfirmationToken());
            modelAndView.setViewName("confirm");
        }

        return modelAndView;        
    }

    // Process confirmation link (POST)
    @RequestMapping(value="/confirm", method = RequestMethod.POST)
    public ModelAndView confirmRegistration(ModelAndView modelAndView, BindingResult bindingResult, @RequestParam Map<String, String> requestParams, RedirectAttributes redir) {
        String token = requestParams.get("token");
        String password = requestParams.get("password");

        Zxcvbn passwordCheck = new Zxcvbn();
        Strength strength = passwordCheck.measure(password);

        if (strength.getScore() < 3) {
            redir.addFlashAttribute("errorMessage", "Your password is too weak. Choose a stronger one.");
            modelAndView.setViewName("redirect:confirm?token=" + token);
            return modelAndView;
        }

        // Find the user associated with the reset token
        User user = userService.findByConfirmationToken(token);

        if (user == null) {
            modelAndView.addObject("invalidToken", "Oops! This is an invalid confirmation link.");
            modelAndView.setViewName("error");
        } else {
            // Set new password (ensure to encode the password in a real application)
            user.setPassword(password);  // replace this with encoding in real application

            // Set user to enabled
            user.setEnabled(true);

            // Save user
            userService.saveUser(user);

            modelAndView.addObject("successMessage", "Your password has been set!");
            modelAndView.setViewName("login");
        }

        return modelAndView;
    }
}
