package com.doubtportal.controller;

import com.doubtportal.model.Doubt;
import com.doubtportal.repository.DoubtRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/doubts")
@CrossOrigin(origins = "*")
public class DoubtController {

    @Autowired
    private DoubtRepository doubtRepository;

    @GetMapping
    public List<Doubt> getAllDoubts() {
        return doubtRepository.findAll();
    }

    @PostMapping
    public Doubt createDoubt(@RequestBody Doubt doubt) {
        if (doubt.getStatus() == null) {
            doubt.setStatus("Pending");
        }
        return doubtRepository.save(doubt);
    }

    @PutMapping("/{id}/close")
    public String closeDoubt(@PathVariable Integer id) {
        doubtRepository.closeDoubtProcedure(id);
        return "Doubt ID " + id + " closed successfully via Stored Procedure.";
    }
}