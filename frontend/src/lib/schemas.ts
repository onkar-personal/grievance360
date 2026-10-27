import { z } from "zod";

// Mirrors server-side rules; the server remains the authority.
export const passwordSchema = z
  .string()
  .min(8, "At least 8 characters")
  .max(72, "At most 72 characters")
  .regex(/[A-Za-z]/, "Include at least one letter")
  .regex(/\d/, "Include at least one number");

const phone = z
  .string()
  .trim()
  .regex(/^$|^[+]?[0-9 ]{10,15}$/, "Enter a valid phone number")
  .optional();

export const loginSchema = z.object({
  email: z.string().trim().min(1, "Email is required").email("Enter a valid email"),
  password: z.string().min(1, "Password is required"),
});
export type LoginValues = z.infer<typeof loginSchema>;

export const registerSchema = z
  .object({
    fullName: z.string().trim().min(2, "Enter your full name").max(120),
    email: z.string().trim().min(1, "Email is required").email("Enter a valid email").max(160),
    phone,
    password: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((v) => v.password === v.confirmPassword, { path: ["confirmPassword"], message: "Passwords do not match" });
export type RegisterValues = z.infer<typeof registerSchema>;

export const complaintSchema = z.object({
  categoryId: z.coerce.number({ invalid_type_error: "Select a category" }).int().positive("Select a category"),
  title: z.string().trim().min(5, "Title must be at least 5 characters").max(160),
  description: z.string().trim().min(20, "Describe the issue in at least 20 characters").max(5000),
  locationAddress: z.string().trim().max(300).optional(),
  locationWard: z.string().trim().max(80).optional(),
  locationCity: z.string().trim().max(80).optional(),
  locationPincode: z
    .string()
    .trim()
    .regex(/^$|^[0-9]{6}$/, "PIN code must be 6 digits")
    .optional(),
});
export type ComplaintValues = z.infer<typeof complaintSchema>;

export const profileSchema = z.object({
  fullName: z.string().trim().min(2, "Enter your full name").max(120),
  phone,
  address: z.string().trim().max(300).optional(),
});
export type ProfileValues = z.infer<typeof profileSchema>;

export const passwordChangeSchema = z
  .object({
    currentPassword: z.string().min(1, "Enter your current password"),
    newPassword: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, { path: ["confirmPassword"], message: "Passwords do not match" });
export type PasswordChangeValues = z.infer<typeof passwordChangeSchema>;

export const ALLOWED_FILE_TYPES = ["image/jpeg", "image/png", "image/webp", "application/pdf"];
export const MAX_FILE_BYTES = 5 * 1024 * 1024;
export const MAX_FILES = 5;

export function validateFile(f: File): string | null {
  if (!ALLOWED_FILE_TYPES.includes(f.type)) return `${f.name}: only JPEG, PNG, WEBP or PDF files are allowed`;
  if (f.size > MAX_FILE_BYTES) return `${f.name}: file is larger than 5 MB`;
  if (f.size === 0) return `${f.name}: file is empty`;
  return null;
}
