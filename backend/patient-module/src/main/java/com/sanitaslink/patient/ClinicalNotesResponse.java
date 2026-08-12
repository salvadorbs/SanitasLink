package com.sanitaslink.patient;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Clinical record (notes) view and write request. */
public record ClinicalNotesResponse(UUID patientId, String clinicalNotes) {

  public record Update(
      @NotNull @Size(max = 10000, message = "clinical notes too long") String clinicalNotes) {}
}
