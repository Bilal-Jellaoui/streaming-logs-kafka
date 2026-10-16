package com.soukline.controller;

import com.soukline.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    public static final String SESSION_USER_KEY = "SOUKLINE_USER";

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "redirect", required = false) String redirect,
            HttpSession session,
            Model model) {

        User currentUser = (User) session.getAttribute(SESSION_USER_KEY);
        if (currentUser != null && currentUser.isAuthenticated()) {
            return redirect != null ? "redirect:" + redirect : "redirect:/";
        }

        model.addAttribute("redirect", redirect);
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(
            @RequestParam("email") String email,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "redirect", required = false) String redirect,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (email == null || email.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Veuillez entrer une adresse email valide.");
            return "redirect:/login";
        }

        String name = email.split("@")[0];
        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        String role = email.toLowerCase().contains("admin") ? "ADMIN" : "CLIENT";

        User user = new User(name, email, role, true);
        session.setAttribute(SESSION_USER_KEY, user);

        redirectAttributes.addFlashAttribute("successMessage", "Bienvenue, " + name + " ! Connexion réussie.");

        if (redirect != null && !redirect.trim().isEmpty() && !redirect.equals("/login")) {
            return "redirect:" + redirect;
        }
        return "redirect:/";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        session.removeAttribute(SESSION_USER_KEY);
        redirectAttributes.addFlashAttribute("infoMessage", "Vous avez été déconnecté.");
        return "redirect:/";
    }
}
