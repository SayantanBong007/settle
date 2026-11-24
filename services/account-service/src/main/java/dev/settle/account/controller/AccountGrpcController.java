package dev.settle.account.controller;

import dev.settle.account.dto.CancelHoldRequestDto;
import dev.settle.account.dto.CancelHoldResponseDto;
import dev.settle.account.dto.CompleteHoldRequestDto;
import dev.settle.account.dto.CompleteHoldResponseDto;
import dev.settle.account.dto.CreateHoldRequestDto;
import dev.settle.account.dto.CreateHoldResponseDto;
import dev.settle.account.exception.AccountNotFoundException;
import dev.settle.account.exception.HoldNotActiveException;
import dev.settle.account.exception.HoldNotFoundException;
import dev.settle.account.exception.IdempotencyConflictException;
import dev.settle.account.exception.InsufficientFundsException;
import dev.settle.account.service.AccountService;
import dev.settle.proto.settle.account.v1.AccountServiceGrpc;
import dev.settle.proto.settle.account.v1.CancelHoldRequest;
import dev.settle.proto.settle.account.v1.CancelHoldResponse;
import dev.settle.proto.settle.account.v1.CompleteHoldRequest;
import dev.settle.proto.settle.account.v1.CompleteHoldResponse;
import dev.settle.proto.settle.account.v1.CreateHoldRequest;
import dev.settle.proto.settle.account.v1.CreateHoldResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

@Component
public class AccountGrpcController extends AccountServiceGrpc.AccountServiceImplBase {

    private final AccountService accountService;

    public AccountGrpcController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public void createHold(CreateHoldRequest request, StreamObserver<CreateHoldResponse> responseObserver) {
        try {
            CreateHoldResponseDto dto = accountService.createHold(new CreateHoldRequestDto(
                    request.getIdempotencyKey(),
                    request.getTransferId(),
                    request.getSourceAccountId(),
                    request.getDestinationAccountId(),
                    request.getAmount(),
                    request.getCurrency()
            ));
            responseObserver.onNext(CreateHoldResponse.newBuilder()
                    .setHoldId(dto.holdId())
                    .setAvailableBalance(dto.availableBalance())
                    .setPendingBalance(dto.pendingBalance())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(mapException(e));
        }
    }

    @Override
    public void completeHold(CompleteHoldRequest request, StreamObserver<CompleteHoldResponse> responseObserver) {
        try {
            CompleteHoldResponseDto dto = accountService.completeHold(new CompleteHoldRequestDto(
                    request.getIdempotencyKey(),
                    request.getHoldId(),
                    request.getDestinationAccountId()
            ));
            responseObserver.onNext(CompleteHoldResponse.newBuilder()
                    .setTransferId(dto.transferId())
                    .setStatus(dto.status())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(mapException(e));
        }
    }

    @Override
    public void cancelHold(CancelHoldRequest request, StreamObserver<CancelHoldResponse> responseObserver) {
        try {
            CancelHoldResponseDto dto = accountService.cancelHold(new CancelHoldRequestDto(
                    request.getIdempotencyKey(),
                    request.getHoldId(),
                    request.getCancelReason()
            ));
            responseObserver.onNext(CancelHoldResponse.newBuilder()
                    .setCancelled(dto.cancelled())
                    .setAvailableBalance(dto.availableBalance())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(mapException(e));
        }
    }

    // Maps named domain exceptions (never string matching!) to gRPC Status codes
    private Throwable mapException(Exception e) {
        if (e instanceof AccountNotFoundException || e instanceof HoldNotFoundException) {
            return Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException();
        }
        if (e instanceof InsufficientFundsException || e instanceof HoldNotActiveException) {
            return Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException();
        }
        if (e instanceof IdempotencyConflictException || e instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException();
        }
        if (e instanceof ConcurrencyFailureException) {
            // Includes PessimisticLockingFailureException, CannotAcquireLockException, OptimisticLockingFailureException
            return Status.ABORTED.withDescription(e.getMessage()).asRuntimeException();
        }
        return Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException();
    }
}
