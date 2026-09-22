package com.casino.backend.controllers;

import com.casino.backend.models.Transaction;
import com.casino.backend.models.User;
import com.casino.backend.models.Wallet;
import com.casino.backend.repositories.TransactionRepository;
import com.casino.backend.repositories.UserRepository;
import com.casino.backend.repositories.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @GetMapping("/balance")
    public String getBalance() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).get();
        Wallet wallet = walletRepository.findByUserId(user.getId());
        if (wallet == null) return "El saldo actual de " + username + " es: $0.0";
        return "El saldo actual de " + username + " es: $" + wallet.getBalance();
    }

    @GetMapping("/history")
    public List<Transaction> getHistory() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).get();
        return transactionRepository.findByUserId(user.getId());
    }

    // DEPÓSITOS VIP CON VALIDACIÓN DE TARJETA BANCARIA
    @PostMapping("/vip-deposit/{username}")
    public Object vipDeposit(@PathVariable String username, @RequestBody Map<String, Object> request) {
        try {
            Object cardNumberObj = request.get("cardNumber");
            if (cardNumberObj == null) return "Error: Faltan los 16 dígitos de tu tarjeta.";
            
            String cardNumber = cardNumberObj.toString().replaceAll("\\s+", ""); 
            if (!cardNumber.matches("\\d{16}")) return "Error: La tarjeta debe ser de exactamente 16 números.";

            Object amountObj = request.get("amount");
            if (amountObj == null) return "Error: No se envió la cantidad.";
            
            Double amount = Double.parseDouble(amountObj.toString());
            if (amount <= 0) return "Error: Ingresa una cantidad mayor a $0.";

            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null) return "Error: El usuario " + username + " no existe.";
            
            Wallet wallet = walletRepository.findByUserId(user.getId());
            if (wallet == null) {
                wallet = new Wallet();
                wallet.setUser(user);
                wallet.setBalance(0.0);
            }

            wallet.setBalance(wallet.getBalance() + amount);
            walletRepository.save(wallet);
            transactionRepository.save(new Transaction("DEPOSITO", amount, wallet.getBalance(), user));

            String ultimos4 = cardNumber.substring(12);
            return "¡Éxito " + username + "! Has depositado $" + amount + " con la tarjeta terminando en " + ultimos4 + ".";

        } catch (Exception e) {
            return "ERROR: " + e.toString();
        }
    }

    // ADMINISTRADOR CON CONTRASEÑA SECRETA
    @PostMapping("/make-me-admin")
    public ResponseEntity<?> makeMeAdmin(@RequestBody Map<String, String> request) {
        String secretKey = request.get("secretKey");
        
        if ("casinobj101217".equals(secretKey)) {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            Optional<User> userOpt = userRepository.findByUsername(username);
            
            if (!userOpt.isPresent()) {
                return ResponseEntity.badRequest().body("Error: No se encontró al usuario.");
            }
            
            User user = userOpt.get();
            user.setRole("ADMIN"); 
            userRepository.save(user); 
            
            return ResponseEntity.ok("¡Felicidades " + username + "! Ahora eres ADMIN. Cierra sesión y vuelve a entrar.");
        }
        
        return ResponseEntity.status(403).body("Error: Contraseña secreta incorrecta.");
    }

    // RESULTADO DEL JUEGO (SUMAR O RESTAR APUESTA)
    @PostMapping("/game-result")
    public ResponseEntity<?> gameResult(@RequestBody Map<String, Double> request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).get();
        Wallet wallet = walletRepository.findByUserId(user.getId());
        
        if (wallet == null) {
            wallet = new Wallet();
            wallet.setUser(user);
            wallet.setBalance(0.0);
        }
        
        Double amount = request.get("amount"); 
        wallet.setBalance(wallet.getBalance() + amount);
        walletRepository.save(wallet);
        
        // Guardar historial de la partida
        String tipo = amount > 0 ? "GANANCIA BLACKJACK" : "PERDIDA BLACKJACK";
        transactionRepository.save(new Transaction(tipo, amount, wallet.getBalance(), user));
        
        return ResponseEntity.ok(wallet.getBalance());
    }
}